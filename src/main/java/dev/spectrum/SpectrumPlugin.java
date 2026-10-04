package dev.spectrum;

import dev.spectrum.command.SpectrumCommand;
import dev.spectrum.command.StyleCommand;
import dev.spectrum.hook.Hooks;
import dev.spectrum.style.PaletteStyle;
import dev.spectrum.style.StyleKind;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.TreeSet;

/**
 * Spectrum: colours for chat messages and player names, defined in chatcolors.yml and namegradients.yml.
 *
 * @author Groovified, Blockie Studios
 */
public class SpectrumPlugin extends JavaPlugin {

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
        messages = new Messages(this);
        styles = new StyleService(this);
        selections = new Selections(this);
        hooks = new Hooks(this);

        reloadAll();

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
        Metrics.start(this);
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
        saveDefaultConfig();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        boolean ok = messages.load();
        ok &= styles.load();
        hooks.load();
        return ok;
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
