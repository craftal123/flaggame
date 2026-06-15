package com.flaggame;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

final class GameManager implements Listener {

    private final FlagGamePlugin plugin;
    private final ImagesBridge images;
    private final CountryCatalog catalog;
    private final Map<UUID, Integer> players = new LinkedHashMap<>();
    private final FlagDeck flagDeck = new FlagDeck();
    private final Set<UUID> skipVotes = new HashSet<>();
    private final Object lock = new Object();

    private FlagDownloader downloader;
    private Country currentCountry;
    private Object currentImage;
    private World gameWorld;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private boolean active;
    private boolean acceptingAnswers;
    private boolean ready;
    private int roundGeneration;

    GameManager(FlagGamePlugin plugin, ImagesBridge images) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.images = images;
        this.catalog = new CountryCatalog();
        reloadConfiguration();
    }

    void reloadConfiguration() throws ReflectiveOperationException {
        this.downloader = new FlagDownloader(
                images.imagesDirectory(),
                plugin.getConfig().getString("image.url", "https://flagcdn.com/w320/%s.png"),
                Math.max(1, plugin.getConfig().getInt("image.width", 384)),
                Math.max(1, plugin.getConfig().getInt("image.height", 256))
        );
    }

    int refreshCatalog() throws IOException {
        URI source = URI.create(plugin.getConfig().getString(
                "country-list-url", "https://flagcdn.com/en/codes.json"));
        return catalog.refresh(
                source,
                plugin.getDataFolder().toPath().resolve("flagcdn-codes.json")
        );
    }

    void join(Player player) {
        synchronized (lock) {
            if (active) {
                message(player, "&cA game is already running.");
                return;
            }
            if (players.containsKey(player.getUniqueId())) {
                message(player, "&eYou already joined.");
                return;
            }
            if (players.size() >= 2) {
                message(player, "&cThe game already has two players.");
                return;
            }
            players.put(player.getUniqueId(), 0);
        }
        broadcast("&e" + player.getName() + " joined the flag game. &7(" + players.size() + "/2)");
    }

    void leave(Player player) {
        synchronized (lock) {
            if (active) {
                message(player, "&cYou cannot leave during a game. Ask an admin to stop it.");
                return;
            }
            if (players.remove(player.getUniqueId()) == null) {
                message(player, "&eYou are not in the game.");
                return;
            }
        }
        broadcast("&e" + player.getName() + " left the flag game.");
    }

    void start(Player sender, Difficulty selectedDifficulty) {
        synchronized (lock) {
            if (!ready) {
                message(sender, "&eFlagGame is still initializing. Try again in a moment.");
                return;
            }
            if (active) {
                message(sender, "&cA game is already running.");
                return;
            }
            if (players.size() != 2) {
                message(sender, "&cExactly two players must join before starting.");
                return;
            }
            if (!players.containsKey(sender.getUniqueId()) && !sender.hasPermission("flaggame.admin")) {
                message(sender, "&cOnly a joined player or an admin can start.");
                return;
            }
            players.replaceAll((uuid, score) -> 0);
            flagDeck.reset();
            skipVotes.clear();
            gameWorld = sender.getWorld();
            difficulty = selectedDifficulty;
            active = true;
            acceptingAnswers = false;
            roundGeneration++;
        }
        broadcast("&aFlag game started on &e" + difficulty.displayName()
                + "&a difficulty! &fFirst player to "
                + plugin.getConfig().getInt("points-to-win", 10) + " points wins.");
        startRound();
    }

    void startTest(Player sender, Difficulty selectedDifficulty) {
        synchronized (lock) {
            if (!ready) {
                message(sender, "&eFlagGame is still initializing. Try again in a moment.");
                return;
            }
            if (active) {
                message(sender, "&cA game is already running.");
                return;
            }

            players.clear();
            players.put(sender.getUniqueId(), 0);
            flagDeck.reset();
            skipVotes.clear();
            gameWorld = sender.getWorld();
            difficulty = selectedDifficulty;
            active = true;
            acceptingAnswers = false;
            roundGeneration++;
        }
        broadcast("&eFlag game test mode started by " + sender.getName()
                + " on " + difficulty.displayName() + " difficulty"
                + ". &fFirst to " + Math.max(1, plugin.getConfig().getInt("points-to-win", 10))
                + " points wins.");
        startRound();
    }

    void stop(String reason) {
        Object image;
        synchronized (lock) {
            if (!active && currentImage == null) {
                return;
            }
            active = false;
            acceptingAnswers = false;
            roundGeneration++;
            image = currentImage;
            currentImage = null;
            currentCountry = null;
            flagDeck.reset();
            skipVotes.clear();
            gameWorld = null;
        }
        if (reason != null) {
            broadcast("&c" + reason);
        }
        removeImageAsync(image);
    }

    void shutdown() {
        Object image;
        synchronized (lock) {
            active = false;
            acceptingAnswers = false;
            roundGeneration++;
            image = currentImage;
            currentImage = null;
            currentCountry = null;
            flagDeck.reset();
            skipVotes.clear();
            gameWorld = null;
        }
        if (image != null) {
            try {
                images.removeImage(image);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Could not remove the current flag during shutdown: "
                        + exception.getMessage());
            }
        }
    }

    void status(Player player) {
        List<String> scores = new ArrayList<>();
        synchronized (lock) {
            for (Map.Entry<UUID, Integer> entry : players.entrySet()) {
                String name = Objects.requireNonNullElse(Bukkit.getOfflinePlayer(entry.getKey()).getName(), "Unknown");
                scores.add(name + ": " + entry.getValue());
            }
            message(player, "&6Flag game: &f" + (active ? "running" : "waiting")
                    + "&7 | Difficulty: &f" + difficulty.displayName()
                    + "&7 | Players: &f" + (scores.isEmpty() ? "none" : String.join(", ", scores)));
        }
    }

    void revealCurrentFlag(Player player) {
        synchronized (lock) {
            if (!active) {
                message(player, "&eNo flag game is currently running.");
                return;
            }
            if (currentCountry == null || !acceptingAnswers) {
                message(player, "&eThe next flag is still loading.");
                return;
            }
            message(player, "&6Current flag: &e" + currentCountry.englishName()
                    + " &7/ &e" + currentCountry.hebrewName()
                    + " &7(" + currentCountry.code() + ")");
        }
    }

    void voteSkip(Player player) {
        Country skippedCountry = null;
        int votes;
        int required;
        int completedGeneration = -1;
        synchronized (lock) {
            if (!active || currentCountry == null || !acceptingAnswers) {
                message(player, "&eThere is no active flag to skip.");
                return;
            }
            if (!players.containsKey(player.getUniqueId())) {
                message(player, "&cOnly players in the current game can vote to skip.");
                return;
            }
            if (!skipVotes.add(player.getUniqueId())) {
                message(player, "&eYou already voted to skip this flag.");
                return;
            }

            votes = skipVotes.size();
            required = players.size();
            if (votes >= required) {
                acceptingAnswers = false;
                skippedCountry = currentCountry;
                completedGeneration = roundGeneration;
                skipVotes.clear();
            }
        }

        if (skippedCountry == null) {
            broadcast("&e" + player.getName() + " voted to skip. &7("
                    + votes + "/" + required + ")");
            return;
        }

        broadcast("&eThe flag was skipped with no points awarded. &fIt was &6"
                + skippedCountry.englishName() + " &7/ &6" + skippedCountry.hebrewName() + "&f.");
        scheduleNextRound(completedGeneration);
    }

    boolean isActive() {
        synchronized (lock) {
            return active;
        }
    }

    void markReady() {
        synchronized (lock) {
            ready = true;
        }
        plugin.getLogger().info("FlagGame is ready.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean correct;
        boolean recognizedCountry;
        synchronized (lock) {
            if (!active || !acceptingAnswers || currentCountry == null
                    || !players.containsKey(player.getUniqueId())) {
                return;
            }
            correct = currentCountry.matches(event.getMessage());
            recognizedCountry = catalog.isKnownCountryAnswer(event.getMessage());
            if (correct) {
                acceptingAnswers = false;
                skipVotes.clear();
            }
        }

        if (correct || recognizedCountry) {
            event.setCancelled(true);
        }
        if (correct) {
            Bukkit.getScheduler().runTask(plugin, () -> awardPoint(player));
        } else if (recognizedCountry) {
            String guess = event.getMessage();
            Bukkit.getScheduler().runTask(plugin,
                    () -> message(player, "&cWrong guess: &f" + guess));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boolean shouldStop;
        synchronized (lock) {
            shouldStop = active && players.containsKey(event.getPlayer().getUniqueId());
            if (!active) {
                players.remove(event.getPlayer().getUniqueId());
            }
        }
        if (shouldStop) {
            stop(event.getPlayer().getName() + " disconnected, so the game was stopped.");
        }
    }

    private void awardPoint(Player player) {
        Country answer;
        int score;
        int completedGeneration;
        int winningScore = Math.max(1, plugin.getConfig().getInt("points-to-win", 10));
        synchronized (lock) {
            if (!active || currentCountry == null || !players.containsKey(player.getUniqueId())) {
                return;
            }
            answer = currentCountry;
            score = players.merge(player.getUniqueId(), 1, Integer::sum);
            completedGeneration = roundGeneration;
        }

        player.showTitle(Title.title(
                Component.text("CORRECT!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(150),
                        Duration.ofMillis(1_200),
                        Duration.ofMillis(350)
                )
        ));
        broadcast("&a" + player.getName() + " was first! &fThe flag was &e"
                + answer.englishName() + " &7/ &e" + answer.hebrewName()
                + "&f. Score: &6" + score + "&f/&6" + winningScore);

        if (score >= winningScore) {
            broadcast("&6" + player.getName() + " wins the flag game!");
            stop(null);
            return;
        }

        scheduleNextRound(completedGeneration);
    }

    private void startRound() {
        final int generation;
        final Country selected;
        final Location location;
        final BlockFace facing;
        synchronized (lock) {
            if (!active) {
                return;
            }
            generation = ++roundGeneration;
            selected = chooseCountry();
            if (selected == null) {
                currentCountry = null;
                acceptingAnswers = false;
                location = null;
                facing = null;
            } else {
                currentCountry = selected;
                acceptingAnswers = false;
                skipVotes.clear();
                location = getGameLocation();
                facing = getFacing();
            }
        }

        if (selected == null) {
            stop("Every flag in " + difficulty.displayName()
                    + " difficulty has been used, so the game ended without repeats.");
            return;
        }
        if (location == null) {
            stop("The game world is no longer loaded.");
            return;
        }

        broadcast("&7Loading the next flag...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                FlagDownloader.DownloadedFlag flag = downloader.load(selected);
                Object previous;
                synchronized (lock) {
                    if (!active || generation != roundGeneration) {
                        return;
                    }
                    previous = currentImage;
                }

                Object placed = images.replaceImage(
                        previous, flag.fileName(), location, facing, flag.image());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    synchronized (lock) {
                        if (!active || generation != roundGeneration) {
                            removeImageAsync(placed);
                            return;
                        }
                        currentImage = placed;
                        acceptingAnswers = true;
                    }
                    broadcast("&bA new flag is shown! &fType the country in English or Hebrew.");
                    sendSkipPrompt();
                });
            } catch (Exception exception) {
                plugin.getLogger().severe("Could not load/place flag " + selected.code() + ": " + exception);
                Bukkit.getScheduler().runTask(plugin,
                        () -> stop("The flag could not be loaded. Check the server log."));
            }
        });
    }

    private Country chooseCountry() {
        List<Country> countries = difficulty.filter(catalog.countries());
        if (countries.isEmpty()) {
            countries = catalog.countries();
        }
        return flagDeck.draw(countries, ThreadLocalRandom.current());
    }

    private void scheduleNextRound(int completedGeneration) {
        long delay = Math.max(1L, plugin.getConfig().getLong("round-delay-ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (lock) {
                if (!active || roundGeneration != completedGeneration) {
                    return;
                }
            }
            startRound();
        }, delay);
    }

    private Location getGameLocation() {
        World world = gameWorld;
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                plugin.getConfig().getDouble("location.x", 0),
                plugin.getConfig().getDouble("location.y", 0),
                plugin.getConfig().getDouble("location.z", 0)
        );
    }

    private BlockFace getFacing() {
        String configured = plugin.getConfig().getString(
                "location.direction",
                plugin.getConfig().getString("location.facing", "NORTH")
        );
        try {
            BlockFace face = BlockFace.valueOf(configured.toUpperCase(Locale.ROOT));
            return switch (face) {
                case NORTH, SOUTH, EAST, WEST -> face;
                default -> BlockFace.NORTH;
            };
        } catch (IllegalArgumentException exception) {
            return BlockFace.NORTH;
        }
    }

    private void removeImageAsync(Object image) {
        if (image == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                images.removeImage(image);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Could not remove the current flag: " + exception.getMessage());
            }
        });
    }

    private void broadcast(String text) {
        Bukkit.broadcastMessage(color("&6[FlagGame] &r" + text));
    }

    private void sendSkipPrompt() {
        Component prompt = Component.text("[FlagGame] ", NamedTextColor.GOLD)
                .append(Component.text("[Click to vote skip]", NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/fg skip"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Vote to skip this flag", NamedTextColor.GRAY))));
        synchronized (lock) {
            for (UUID uuid : players.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(prompt);
                }
            }
        }
    }

    private void message(Player player, String text) {
        player.sendMessage(color("&6[FlagGame] &r" + text));
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
