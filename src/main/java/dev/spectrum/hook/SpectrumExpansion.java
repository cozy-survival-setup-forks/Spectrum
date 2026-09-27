package dev.spectrum.hook;

import dev.spectrum.SpectrumPlugin;
import dev.spectrum.style.Style;
import dev.spectrum.style.StyleKind;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI values. {@code <kind>} is {@code name} or {@code chat}.
 * <pre>
 * %spectrum_name%                        the player's name in their name gradient (or with the colours of their nickname), with &amp;#rrggbb codes
 * %spectrum_realname%                    the player's own name, with no colours
 * %spectrum_&lt;kind&gt;_id%                   the id of the style the player has active, or "none"
 * %spectrum_&lt;kind&gt;_display%              its name, coloured, with &amp;#rrggbb codes (MiniMessage for a glitch style)
 * %spectrum_&lt;kind&gt;_equipped_&lt;id&gt;        true if that style is active
 * %spectrum_&lt;kind&gt;_owned_&lt;id&gt;           true if the player may use that style
 * %spectrum_&lt;kind&gt;_preview_&lt;id&gt;         the player's name (name) or the preview text (chat) in that style, with &amp;#rrggbb codes (MiniMessage for a glitch style)
 * </pre>
 * Only loaded when PlaceholderAPI is installed.
 */
final class SpectrumExpansion extends PlaceholderExpansion {

    private final SpectrumPlugin plugin;

    SpectrumExpansion(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "spectrum";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String getRequiredPlugin() {
        // Without this, PlaceholderAPI has no reason to unregister the expansion when Spectrum is
        // disabled (by a plugin manager or a reload), so it keeps serving from the old classloader.
        return "Spectrum";
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        String request = params.toLowerCase(Locale.ROOT);

        if (request.equals("name")) {
            String styled = plugin.hooks().styledName(player);
            return styled == null ? plugin.hooks().nameOf(player) : styled;
        }
        // The player's own name, for commands, when %player_name% gives the coloured name
        if (request.equals("realname")) return player.getName();

        for (StyleKind kind : StyleKind.values()) {
            String prefix = kind.key() + "_";
            if (request.startsWith(prefix)) return kindValue(player, kind, request.substring(prefix.length()));
        }
        return null;
    }

    private @Nullable String kindValue(Player player, StyleKind kind, String request) {
        Style active = plugin.styles().equipped(player, kind);
        switch (request) {
            case "id" -> {
                return active == null ? "none" : active.id();
            }
            case "display" -> {
                return active == null ? "None" : active.ampersand(active.display());
            }
            default -> {
                // fall through to the ones that take an id
            }
        }

        if (request.startsWith("equipped_")) {
            return String.valueOf(active != null && active.id().equals(request.substring(9)));
        }
        if (request.startsWith("owned_")) {
            Style style = plugin.styles().library(kind).get(request.substring(6));
            return String.valueOf(style != null && plugin.styles().canUse(player, style));
        }
        if (request.startsWith("preview_")) {
            // Only computed here, not above: it's a full PlaceholderAPI parse of name-source for the
            // name kind, and equipped_/owned_ never use it.
            String preview = kind == StyleKind.NAME ? plugin.hooks().nameOf(player) : plugin.settings().chatPreviewText();
            return previewOf(kind, request.substring(8), style -> style.ampersand(preview));
        }
        return null;
    }

    private String previewOf(StyleKind kind, String id, java.util.function.Function<Style, String> format) {
        Style style = plugin.styles().library(kind).get(id);
        return style == null ? "" : format.apply(style);
    }
}
