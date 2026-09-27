package dev.spectrum;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts in messages.yml, written in MiniMessage. Old style codes such as &amp;7 and &amp;#rrggbb work too.
 * Messages missing from an older file fall back to the default one.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final String[] TAGS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private final SpectrumPlugin plugin;
    private FileConfiguration file = new YamlConfiguration();

    Messages(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) plugin.saveResource("messages.yml", false);
        file = YamlConfiguration.loadConfiguration(target);

        var defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
            // Every lookup below passes its own explicit default ("") to getString/getStringList,
            // which skips consulting getDefaults() entirely - without this, a key missing from an
            // admin's older messages.yml just goes silently empty instead of using the bundled text.
            file.options().copyDefaults(true);
        }
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

    /** Sends a message with the prefix. Empty messages are skipped, so any of them can be turned off. */
    public void send(CommandSender to, String key, TagResolver... resolvers) {
        if (file.isList(key)) {
            for (String line : file.getStringList(key)) {
                to.sendMessage(MINI.deserialize(convertLegacy(line.replace("{prefix}", file.getString("prefix", ""))), resolvers));
            }
            return;
        }
        String text = file.getString(key, "");
        if (text.isEmpty()) return;
        to.sendMessage(MINI.deserialize(convertLegacy(file.getString("prefix", "") + text), resolvers));
    }
}
