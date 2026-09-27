package dev.spectrum;

import dev.spectrum.style.Style;
import dev.spectrum.style.StyleKind;
import dev.spectrum.style.StyleLibrary;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads the style files and answers what a player is allowed to use and what they have picked.
 */
public final class StyleService {

    private final SpectrumPlugin plugin;
    private volatile Map<StyleKind, StyleLibrary> libraries = new EnumMap<>(StyleKind.class);

    StyleService(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads both style files. Returns false if either one is broken - the styles it held stay in place. */
    boolean load() {
        Map<StyleKind, StyleLibrary> loaded = new EnumMap<>(StyleKind.class);
        boolean ok = true;
        for (StyleKind kind : StyleKind.values()) {
            File file = new File(plugin.getDataFolder(), kind.fileName());
            if (!file.exists()) plugin.saveResource(kind.fileName(), false);
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                loaded.put(kind, StyleLibrary.load(kind, yaml, plugin.getLogger(), plugin.settings().glitch()));
            } catch (InvalidConfigurationException | IOException e) {
                // YamlConfiguration.loadConfiguration(File) would otherwise swallow this and hand back
                // an empty config, which wipes every style of this kind with no error beyond a console log.
                plugin.getLogger().warning(kind.fileName() + " is broken, keeping the styles already loaded: " + e.getMessage());
                ok = false;
                StyleLibrary previous = libraries.get(kind);
                if (previous != null) loaded.put(kind, previous);
            }
        }
        libraries = loaded;
        return ok;
    }

    public StyleLibrary library(StyleKind kind) {
        return libraries.get(kind);
    }

    /**
     * Can the player use this style? Everyone can when permissions are turned off. Players with the wildcard
     * of the kind (spectrum.chat.* or spectrum.name.*) can use all of them.
     */
    public boolean canUse(Player player, Style style) {
        if (!plugin.settings().usePermissions()) return true;
        return player.hasPermission(style.permission())
                || player.hasPermission(style.kind().defaultPermission().replace("{id}", "*"));
    }

    /**
     * The style a player has active: the one they picked, or the default of the file. Null if they have none, or
     * lost the permission for it.
     */
    public @Nullable Style equipped(Player player, StyleKind kind) {
        StyleLibrary library = library(kind);
        Style picked = library.get(plugin.selections().get(player.getUniqueId(), kind));
        if (picked != null && canUse(player, picked)) {
            return picked;
        }
        // The pick is missing, or a permission the player used to have was taken away - fall back
        // to the server default instead of leaving them with no style at all.
        if (!library.defaultId().isEmpty()) {
            Style fallback = library.get(library.defaultId());
            if (fallback != null && canUse(player, fallback)) {
                return fallback;
            }
        }
        return null;
    }
}
