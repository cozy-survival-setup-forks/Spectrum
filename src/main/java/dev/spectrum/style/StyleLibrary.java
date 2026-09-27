package dev.spectrum.style;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * All the styles of one kind, in the order of the file.
 */
public final class StyleLibrary {

    private final StyleKind kind;
    private final Map<String, Style> styles;
    private final String defaultId;

    private StyleLibrary(StyleKind kind, Map<String, Style> styles, String defaultId) {
        this.kind = kind;
        this.styles = styles;
        this.defaultId = defaultId;
    }

    /**
     * @param root the whole file: {@code default:} and {@code styles:}
     */
    public static StyleLibrary load(StyleKind kind, ConfigurationSection root, Logger logger) {
        return load(kind, root, logger, GlitchOptions.OFF);
    }

    public static StyleLibrary load(StyleKind kind, ConfigurationSection root, Logger logger, GlitchOptions glitch) {
        Map<String, Style> styles = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("styles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    logger.warning(kind.fileName() + ": '" + key + "' is not a section, skipping it.");
                    continue;
                }
                Style style = StyleParser.parse(kind, key, entry, logger, glitch);
                if (style == null) continue;
                if (styles.putIfAbsent(style.id(), style) != null) {
                    logger.warning(kind.fileName() + ": the id '" + style.id() + "' is used twice, the first one is kept.");
                }
            }
        }

        String defaultId = StyleParser.cleanId(root.getString("default", ""));
        if (!defaultId.isEmpty() && !styles.containsKey(defaultId)) {
            logger.warning(kind.fileName() + ": the default style '" + defaultId + "' does not exist.");
            defaultId = "";
        }
        return new StyleLibrary(kind, Collections.unmodifiableMap(styles), defaultId);
    }

    public @Nullable Style get(@Nullable String id) {
        return id == null ? null : styles.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Style> all() {
        return styles.values();
    }

    /** Players who did not pick a style get this one (if they may use it). Empty for none. */
    public String defaultId() {
        return defaultId;
    }
}
