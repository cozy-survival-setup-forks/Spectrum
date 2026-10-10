package dev.spectrum;

import dev.spectrum.command.SpectrumCommand;
import dev.spectrum.command.StyleCommand;
import dev.spectrum.hook.Hooks;
import dev.spectrum.safe.ConfigMigrator;
import dev.spectrum.safe.Doctor;
import dev.spectrum.safe.FileBackups;
import dev.spectrum.safe.Guard;
import dev.spectrum.safe.Health;
import dev.spectrum.safe.Prep;
import dev.spectrum.safe.ServerId;
import dev.spectrum.style.PaletteStyle;
import dev.spectrum.style.StyleKind;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spectrum: colours for chat messages and player names, defined in chatcolors.yml and namegradients.yml.
 *
 * @author Groovified, Blockie Studios
 */
public class SpectrumPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static final int LANG_VERSION = 1;

    private final List<Prep.Spec> files = List.of(
            new Prep.Spec("config.yml", "config-version", CONFIG_VERSION, Prep.configMigrator(CONFIG_VERSION), null),
            new Prep.Spec("messages.yml", "lang-version", LANG_VERSION, new ConfigMigrator("lang-version", LANG_VERSION), null));
    private boolean started;

    // Read from the async chat thread in SpectrumListener, written from the main thread on reload.
    private volatile Settings settings;
    private Messages messages;
    private StyleService styles;
    private Selections selections;
    private Hooks hooks;

    @Override
    public void onEnable() {
        try {
            enableInner();
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Spectrum failed to start and will be disabled. This "
                    + "is usually a bad config.yml, messages.yml, chatcolors.yml or namegradients.yml - check the "
                    + "warnings above this, or delete the whole plugins/Spectrum folder to regenerate defaults.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void enableInner() {
        saveDefaultConfig();
        Health.storage("YAML files in the plugin folder (config.yml, messages.yml, chatcolors.yml, namegradients.yml, data.yml)");
        Prep.startup(this, files);
        messages = new Messages(this);
        styles = new StyleService(this);
        selections = new Selections(this);
        hooks = new Hooks(this);

        reloadAll();
        started = true;

        for (StyleKind kind : StyleKind.values()) {
            StyleCommand command = new StyleCommand(this, kind);
            PluginCommand registered = getCommand(kind.command());
            if (registered != null) {
                registered.setExecutor(command);
                registered.setTabCompleter(command);
            }
        }
        SpectrumCommand admin = new SpectrumCommand(this);
        PluginCommand spectrum = getCommand("spectrum");
        if (spectrum != null) {
            spectrum.setExecutor(admin);
            spectrum.setTabCompleter(admin);
        }

        Bukkit.getPluginManager().registerEvents(new SpectrumListener(this), this);
        hooks.registerPlaceholders();

        // Players who are already online (after a reload of the plugin).
        for (Player player : Bukkit.getOnlinePlayers()) selections.load(player);

        // Once every plugin is enabled, see who else touches chat the old way.
        Bukkit.getScheduler().runTask(this, this::warnAboutLegacyChatPlugins);
        boolean beacon = getConfig().getBoolean("metrics.enabled", true);
        Metrics.start(this, ServerId.resolve(getDataFolder().toPath(),
                ServerId.inYaml(new File(getDataFolder(), "data.yml").toPath(), getLogger()), beacon, getLogger()));
        Banner.print(this, "Thanks for keeping every server's chat a little more colourful.");
    }

    /**
     * A plugin that sets the chat format through the old AsyncPlayerChatEvent makes Paper turn the message into a
     * plain string, which drops the shadow of glitch colours. Colours themselves survive, so this only matters when a
     * glitch style exists.
     */
    @SuppressWarnings("deprecation") // read-only detection of legacy listeners, not a functional use of the event
    private void warnAboutLegacyChatPlugins() {
        boolean glitch = styles.library(StyleKind.CHAT).all().stream()
                .anyMatch(style -> style instanceof PaletteStyle palette && palette.glitch());
        if (!glitch) return;

        Set<String> names = new TreeSet<>();
        for (RegisteredListener listener : AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            if (listener.getPlugin() != this) names.add(listener.getPlugin().getName());
        }
        if (!names.isEmpty()) {
            getLogger().info("Glitch chat colours lose their shadow if one of these plugins changes the chat format through "
                    + "the old chat event: " + String.join(", ", names) + ". Use a chat formatter that uses Paper's chat "
                    + "renderer (Quill does) if glitch colours show without the shadow.");
        }
    }

    /** Reloads config.yml, messages.yml and the two style files. Returns false if messages.yml or a style file was broken. */
    public boolean reloadAll() {
        if (started) {
            List<Guard.Problem> problems = Prep.validate(this, files);
            if (!problems.isEmpty()) {
                Prep.logRejected(this, problems);
                return false;
            }
        }
        saveDefaultConfig();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        boolean ok = messages.load();
        ok &= styles.load();
        hooks.load();
        return ok;
    }

    /** The text of /spectrum doctor. */
    public List<String> doctor() {
        List<String> extra = new ArrayList<>(Prep.versionLines(this, files));
        extra.add("Pending writes: 0 (this plugin keeps no queued saves)");
        return Doctor.report(getName(), getPluginMeta().getVersion(), extra);
    }

    /** /spectrum backup now: a verified copy of the settings and data files. */
    public boolean backupNow() {
        List<String> names = new ArrayList<>(Prep.fileNames(files));
        names.add("chatcolors.yml");
        names.add("namegradients.yml");
        names.add("data.yml");
        return FileBackups.snapshot(getDataFolder().toPath(), names, 5, getLogger());
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public StyleService styles() {
        return styles;
    }

    public Selections selections() {
        return selections;
    }

    public Hooks hooks() {
        return hooks;
    }
}
