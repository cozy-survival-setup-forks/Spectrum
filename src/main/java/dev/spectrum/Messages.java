package dev.spectrum;

import dev.spectrum.style.StyleKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts in messages.yml, written in MiniMessage. Old style codes such as &amp;7 and &amp;#rrggbb work too.
 * Chat colour and name gradient messages have their own prefix and their own texts (chat-equipped, name-equipped);
 * a message without one of those falls back to the shared key (equipped), and a key missing from an older file
 * falls back to the bundled text.
 */
public final class Messages {

    /** Whose prefix a message gets: general ones (reload, usage) or the chat colour / name gradient side. */
    public enum Kind {
        GENERAL("prefix", ""),
        CHAT("prefix-chat", "chat-"),
        NAME("prefix-name", "name-");

        private final String prefixKey;
        private final String keyPrefix;

        Kind(String prefixKey, String keyPrefix) {
            this.prefixKey = prefixKey;
            this.keyPrefix = keyPrefix;
        }

        public static Kind of(StyleKind kind) {
            return kind == StyleKind.CHAT ? CHAT : NAME;
        }
    }

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final String[] TAGS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private final SpectrumPlugin plugin;
    private FileConfiguration user = new YamlConfiguration();
    private FileConfiguration bundled = new YamlConfiguration();

    Messages(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads messages.yml. A file with a mistake is reported and the texts loaded before it stay. Returns false then. */
    boolean load() {
        var defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            try (var reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                bundled = YamlConfiguration.loadConfiguration(reader);
            } catch (IOException ignored) {
                // the bundled copy is read from the jar, so there is nothing an admin could fix
            }
        }

        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(target);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("messages.yml could not be read, keeping the messages already loaded: " + e.getMessage());
            return false;
        }
        user = loaded;
        return true;
    }

    /** Turns &amp; codes into MiniMessage tags. */
    static String convertLegacy(String text) {
        String result = HEX.matcher(text).replaceAll("<#$1>");
        return CODE.matcher(result).replaceAll(match -> Matcher.quoteReplacement(tagFor(match.group(1).charAt(0))));
    }

    private static String tagFor(char code) {
        char lower = Character.toLowerCase(code);
        int index = "0123456789abcdef".indexOf(lower);
        if (index >= 0) return "<" + TAGS[index] + ">";
        return switch (lower) {
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            default -> "<reset>";
        };
    }

    /**
     * Finds the text of a message: the admin's file first, then the bundled one. In each, the key of the kind
     * (chat-equipped) wins over the shared key (equipped). Returns null if there is none.
     */
    static Object pick(Kind kind, String key, Function<String, Object> user, Function<String, Object> bundled) {
        String[] keys = kind == Kind.GENERAL ? new String[]{key} : new String[]{kind.keyPrefix + key, key};
        for (var source : List.of(user, bundled)) {
            for (String candidate : keys) {
                Object value = source.apply(candidate);
                if (value instanceof String || value instanceof List) return value;
            }
        }
        return null;
    }

    private String prefix(Kind kind) {
        return pick(Kind.GENERAL, kind.prefixKey, user::get, bundled::get) instanceof String text ? text : "";
    }

    private static Component render(String text, TagResolver[] resolvers) {
        return MINI.deserialize(convertLegacy(text), resolvers);
    }

    /** Sends a message with the prefix of its kind. Empty messages are skipped, so any of them can be turned off. */
    public void send(CommandSender to, Kind kind, String key, TagResolver... resolvers) {
        Object value = pick(kind, key, user::get, bundled::get);
        String prefix = prefix(kind);
        if (value instanceof List<?> lines) {
            for (Object line : lines) to.sendMessage(render(String.valueOf(line).replace("{prefix}", prefix), resolvers));
        } else if (value instanceof String text && !text.isEmpty()) {
            to.sendMessage(render(prefix + text, resolvers));
        }
    }
}
