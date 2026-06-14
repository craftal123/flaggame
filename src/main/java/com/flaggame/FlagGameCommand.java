package com.flaggame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class FlagGameCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "leave", "start", "test", "skip", "what", "stop", "status", "reload");
    private static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");

    private final FlagGamePlugin plugin;
    private final GameManager game;

    FlagGameCommand(FlagGamePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(subcommand)) {
            if (sender instanceof Player player) {
                game.status(player);
            } else {
                sender.sendMessage("Flag game active: " + game.isActive());
            }
            return true;
        }

        if ("stop".equals(subcommand)) {
            if (!sender.hasPermission("flaggame.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            game.stop("The game was stopped by " + sender.getName() + ".");
            return true;
        }

        if ("reload".equals(subcommand)) {
            if (!sender.hasPermission("flaggame.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            if (game.isActive()) {
                sender.sendMessage(ChatColor.RED + "Stop the current game before reloading.");
                return true;
            }
            try {
                plugin.reloadConfig();
                game.reloadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "FlagGame configuration reloaded.");
            } catch (ReflectiveOperationException exception) {
                sender.sendMessage(ChatColor.RED + "Reload failed. Check the server log.");
                plugin.getLogger().severe("Could not reload configuration: " + exception);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be used by a player.");
            return true;
        }
        if ("test".equals(subcommand)) {
            if (!player.hasPermission("flaggame.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            Difficulty difficulty = parseDifficulty(sender, args);
            if (difficulty != null) {
                game.startTest(player, difficulty);
            }
            return true;
        }
        if ("what".equals(subcommand)) {
            if (!player.hasPermission("flaggame.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            game.revealCurrentFlag(player);
            return true;
        }
        if (!player.hasPermission("flaggame.play")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        switch (subcommand) {
            case "join" -> game.join(player);
            case "leave" -> game.leave(player);
            case "skip" -> game.voteSkip(player);
            case "start" -> {
                Difficulty difficulty = parseDifficulty(sender, args);
                if (difficulty != null) {
                    game.start(player, difficulty);
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2
                && ("start".equalsIgnoreCase(args[0]) || "test".equalsIgnoreCase(args[0]))) {
            if ("test".equalsIgnoreCase(args[0]) && !sender.hasPermission("flaggame.admin")) {
                return List.of();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return DIFFICULTIES.stream()
                    .filter(difficulty -> difficulty.startsWith(prefix))
                    .toList();
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(prefix)
                    && (!List.of("test", "what", "stop", "reload").contains(subcommand)
                    || sender.hasPermission("flaggame.admin"))) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "FlagGame commands:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " join"
                + ChatColor.GRAY + " - Join the two-player game");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " leave"
                + ChatColor.GRAY + " - Leave before it starts");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " start"
                + ChatColor.GRAY + " <easy|medium|hard> - Start when two players joined");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status"
                + ChatColor.GRAY + " - Show players and scores");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " skip"
                + ChatColor.GRAY + " - Vote to skip the current flag");
        if (sender.hasPermission("flaggame.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " test"
                    + ChatColor.GRAY + " <easy|medium|hard> - Start a solo test game");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " what"
                    + ChatColor.GRAY + " - Privately reveal the current flag");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " stop|reload");
        }
    }

    private Difficulty parseDifficulty(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /flaggame " + args[0]
                    + " <easy|medium|hard>");
            return null;
        }

        Difficulty difficulty = Difficulty.parse(args.length == 2 ? args[1] : null);
        if (difficulty == null) {
            sender.sendMessage(ChatColor.RED + "Unknown difficulty. Choose easy, medium, or hard.");
        }
        return difficulty;
    }
}
