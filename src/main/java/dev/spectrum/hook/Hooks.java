package dev.spectrum.hook;

import dev.spectrum.SpectrumPlugin;
import dev.spectrum.style.Style;
import dev.spectrum.style.StyleKind;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * Optional plugins. Spectrum works without them.
 */
public final class Hooks {

    private static final Pattern OLD_CODES = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|x(?:[&§][0-9a-f]){6}|[0-9a-fk-or])");
    // Legacy codes only - NOT MiniMessage tags. A nickname plugin gates &-codes; it has no idea what
    // <gradient>/<rainbow>/<click> mean, so treating those as "the nickname's own colour" would let a
    // player get a gradient with no spectrum.name permission, or smuggle a click/hover tag into a name.
    private static final Pattern HAS_COLOURS = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|x(?:[&§][0-9a-f]){6}|[0-9a-fk-o])");
    private static final Pattern X_HEX = Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");
    private static final Pattern STRAY_BRACKETS = Pattern.compile("[<>\\\\]");
    // Matches a placeholder token PlaceholderAPI left untouched, e.g. the whole name-source string
    // coming back unchanged because the expansion it names (Essentials, etc.) isn't installed.
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("%[a-zA-Z0-9_-]+%");

    private final SpectrumPlugin plugin;
    private volatile boolean placeholderApi = false;

    public Hooks(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        placeholderApi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void registerPlaceholders() {
        if (placeholderApi) PapiSupport.register(plugin);
    }

    /**
     * The name to colour: the name-source placeholder from config.yml (a nickname, for example) or the player's
     * name, without any colours it already has.
     */
    public String nameOf(Player player) {
        String source = plugin.settings().nameSource();
        if (source.isBlank() || !placeholderApi) return player.getName();

        String parsed = PapiSupport.parse(player, source);
        if (isUnresolved(parsed, source)) return player.getName();
        String name = plainText(parsed);
        return name.isBlank() ? player.getName() : name;
    }

    /**
     * True when PlaceholderAPI handed the placeholder back unresolved - the expansion it names (e.g.
     * an Essentials nickname placeholder with Essentials not installed) isn't there. Otherwise every
     * player's name becomes the literal placeholder text.
     */
    private static boolean isUnresolved(String parsed, String source) {
        return parsed.equals(source) || UNRESOLVED_PLACEHOLDER.matcher(parsed).find();
    }


    /**
     * The name with its colours, or null when there is nothing to colour: the nickname as it was typed if it has
     * colours of its own (and that is allowed), else the name in the gradient the player has picked.
     */
    public String styledName(Player player) {
        String source = plugin.settings().nameSource();
        if (plugin.settings().nicknameColorsWin() && !source.isBlank() && placeholderApi) {
            String parsed = PapiSupport.parse(player, source);
            if (!isUnresolved(parsed, source)) {
                // Strip any MiniMessage tags before the colour check, but keep legacy codes - that's
                // the whole point of this branch. A leftover < or > (from a broken/nested tag stripTags
                // didn't fully unwrap) is removed outright rather than risked, since it could re-form a
                // click or hover tag once this reaches a formatter that parses placeholders as MiniMessage.
                String raw = STRAY_BRACKETS.matcher(MiniMessage.miniMessage().stripTags(parsed)).replaceAll("").trim();
                if (!raw.isBlank() && HAS_COLOURS.matcher(raw).find()) return ampersand(raw);
            }
        }
        Style style = plugin.styles().equipped(player, StyleKind.NAME);
        return style == null ? null : style.ampersand(nameOf(player));
    }

    /** Old colour codes in any form as &amp; codes with &amp;#rrggbb, so one format is left. */
    static String ampersand(String text) {
        String result = text.replace('§', '&');
        return X_HEX.matcher(result).replaceAll(match -> "&#" + match.group(1).replace("&", ""));
    }

    /** The text without colour codes and MiniMessage tags. */
    static String plainText(String text) {
        String withoutCodes = OLD_CODES.matcher(text).replaceAll("");
        String withoutTags = MiniMessage.miniMessage().stripTags(withoutCodes);
        // A nested/malformed sequence (e.g. "&&cc" or "<<red>red>") can leave a code or bracket
        // fragment behind after one pass of the two strips above - remove any of those characters
        // outright rather than risk a leftover fragment reforming a code or tag downstream.
        return withoutTags.replaceAll("[&§<>\\\\]", "").trim();
    }
}
