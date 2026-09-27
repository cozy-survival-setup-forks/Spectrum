package dev.spectrum;

import dev.spectrum.style.Style;
import dev.spectrum.style.StyleKind;
import dev.spectrum.style.TemplateStyle;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Colours chat messages, and keeps track of what players picked.
 */
public final class SpectrumListener implements Listener {

    private final SpectrumPlugin plugin;

    SpectrumListener(SpectrumPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.selections().load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.selections().forget(event.getPlayer().getUniqueId());
    }

    /**
     * Runs late, so the text is coloured after other plugins changed it. What other plugins put around the
     * message (the format of the chat) is not touched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Style style = plugin.styles().equipped(player, StyleKind.CHAT);
        if (style == null) return;

        // A message another plugin already turned into something with a hover/click event, an
        // insertion, or a non-text component (an [item] preview, a mention) can't be flattened to
        // plain text and recoloured without destroying that - leave it exactly as it is instead.
        if (hasRichContent(event.message())) return;

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (text.isBlank()) return;

        // Only a MiniMessage-aware style can be given tags safely - a palette/gradient style paints
        // every character regardless, so a converted <red> tag would show up as literal text.
        boolean tags = style instanceof TemplateStyle
                && plugin.settings().allowColorCodes() && player.hasPermission("spectrum.chatcodes");
        if (tags) text = Messages.convertLegacy(text);
        event.message(style.render(text, tags));
    }

    private static boolean hasRichContent(Component component) {
        if (component.clickEvent() != null || component.hoverEvent() != null || component.insertion() != null) {
            return true;
        }
        if (!(component instanceof TextComponent)) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasRichContent(child)) return true;
        }
        return false;
    }
}
