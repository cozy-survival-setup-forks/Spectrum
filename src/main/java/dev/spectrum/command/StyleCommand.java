package dev.spectrum.command;

import dev.spectrum.Messages;
import dev.spectrum.SpectrumPlugin;
import dev.spectrum.style.Style;
import dev.spectrum.style.StyleKind;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /chatcolor and /namegradient. They work the same, one for each kind of style:
 * <pre>
 * /namegradient equip &lt;id&gt;
 * /namegradient unequip
 * /namegradient list
 * /namegradient admin give|remove &lt;player&gt; &lt;id&gt;
 * </pre>
 */
public final class StyleCommand implements TabExecutor {

    private final SpectrumPlugin plugin;
    private final StyleKind kind;

    public StyleCommand(SpectrumPlugin plugin, StyleKind kind) {
        this.plugin = plugin;
        this.kind = kind;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "equip", "set" -> equip(sender, args);
            case "unequip", "off", "disable" -> unequip(sender);
            case "list" -> list(sender);
            case "admin" -> admin(sender, args);
            default -> send(sender, "usage-" + kind.key());
        }
        return true;
    }

    /** Sends a message with the prefix and texts of this kind (chat colour or name gradient). */
    private void send(CommandSender to, String key, TagResolver... resolvers) {
        plugin.messages().send(to, Messages.Kind.of(kind), key, resolvers);
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            if (player.hasPermission("spectrum.use")) return player;
            send(sender, "no-permission");
            return null;
        }
        send(sender, "players-only");
        return null;
    }

    private void equip(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        if (args.length < 2) {
            send(sender, "usage-" + kind.key());
            return;
        }

        Style style = plugin.styles().library(kind).get(args[1]);
        if (style == null) {
            send(sender, "invalid-style");
            return;
        }
        if (!plugin.styles().canUse(player, style)) {
            send(sender, "not-owned", Placeholder.component("style", style.displayComponent()));
            return;
        }

        plugin.selections().set(player, kind, style.id());
        send(sender, "equipped", Placeholder.component("style", style.displayComponent()));
    }

    private void unequip(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;

        plugin.selections().set(player, kind, null);
        send(sender, "unequipped");
    }

    private void list(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;

        List<Style> owned = plugin.styles().library(kind).all().stream()
                .filter(style -> plugin.styles().canUse(player, style)).toList();
        if (owned.isEmpty()) {
            send(sender, "list-empty");
            return;
        }
        send(sender, "list-header");
        for (Style style : owned) {
            send(sender, "list-entry", Placeholder.component("style", style.displayComponent()),
                    Placeholder.unparsed("id", style.id()));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spectrum.admin")) {
            send(sender, "no-permission");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length < 4 || !(action.equals("give") || action.equals("remove"))) {
            send(sender, "usage-" + kind.key());
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "player-not-found", Placeholder.unparsed("player", args[2]));
            return;
        }
        Style style = plugin.styles().library(kind).get(args[3]);
        if (style == null) {
            send(sender, "invalid-style");
            return;
        }

        var tags = new TagResolver[]{
                Placeholder.component("style", style.displayComponent()),
                Placeholder.unparsed("player", target.getName())};
        if (action.equals("give")) {
            if (!runCommand(plugin.settings().permissionSetCommand(), target, style)) {
                send(sender, "admin-not-configured");
                return;
            }
            send(sender, "admin-give", tags);
        } else {
            if (!runCommand(plugin.settings().permissionUnsetCommand(), target, style)) {
                send(sender, "admin-not-configured");
                return;
            }
            if (style.id().equals(plugin.selections().get(target.getUniqueId(), kind))) {
                plugin.selections().set(target, kind, null);
            }
            send(sender, "admin-remove", tags);
        }
    }

    /** Runs a command from config.yml as the console, with {player} and {permission} filled in. Returns false if none is set. */
    private boolean runCommand(String template, Player target, Style style) {
        if (template.isBlank()) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                template.replace("{player}", target.getName()).replace("{permission}", style.permission()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("spectrum.admin");

        if (args.length == 1) {
            options.addAll(List.of("equip", "unequip", "list"));
            if (admin) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("equip") && sender instanceof Player player) {
            plugin.styles().library(kind).all().stream()
                    .filter(style -> plugin.styles().canUse(player, style)).forEach(style -> options.add(style.id()));
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) options.addAll(List.of("give", "remove"));
            if (args.length == 3) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            if (args.length == 4) plugin.styles().library(kind).all().forEach(style -> options.add(style.id()));
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
