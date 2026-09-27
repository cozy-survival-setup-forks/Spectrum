package dev.spectrum;

import dev.spectrum.style.GlitchOptions;
import dev.spectrum.style.Palette;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * The options in config.yml.
 */
public final class Settings {

    private final FileConfiguration config;
    private final Logger log;

    Settings(FileConfiguration config, Logger log) {
        this.config = config;
        this.log = log;
    }

    /** When off, every player can use every style. */
    public boolean usePermissions() {
        return config.getBoolean("use-permissions", true);
    }

    /** The placeholder that gives the name to colour, for example a nickname. Empty for the player name. */
    public String nameSource() {
        return config.getString("name-source", "");
    }

    /** When the name from name-source has colours of its own (a /nick with &e), they are used instead of the gradient. */
    public boolean nicknameColorsWin() {
        return config.getBoolean("nickname-colors-win", true);
    }

    /** Whether players with spectrum.chatcodes can write colour codes and MiniMessage in their messages. */
    public boolean allowColorCodes() {
        return config.getBoolean("chat.allow-color-codes", false);
    }

    /** The text shown in the preview placeholders of chat styles. */
    public String chatPreviewText() {
        return config.getString("chat.preview-text", "The quick brown fox");
    }

    /** The glitch look for chat colours: white letters with the colour of the style as their shadow. */
    public GlitchOptions glitch() {
        String raw = config.getString("glitch.text", "white");
        TextColor text = Palette.parseColor(raw);
        if (text == null) {
            log.warning("glitch.text: '" + raw + "' is not a colour, using white");
            text = NamedTextColor.WHITE;
        }
        return new GlitchOptions(config.getBoolean("glitch.all", false), text);
    }

    /** The command that gives a player the permission of a style. {player} and {permission} are filled in. */
    public String permissionSetCommand() {
        return config.getString("commands.permission-set", "");
    }

    public String permissionUnsetCommand() {
        return config.getString("commands.permission-unset", "");
    }
}
