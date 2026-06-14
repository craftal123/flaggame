package com.flaggame;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlagGamePlugin extends JavaPlugin {

    private GameManager game;
    private ImagesBridge images;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLegacyImageSize();
        try {
            this.images = new ImagesBridge();
            this.game = new GameManager(this, images);
        } catch (Exception exception) {
            getLogger().severe("FlagGame requires a compatible Andavin Images plugin: " + exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        FlagGameCommand commandHandler = new FlagGameCommand(this, game);
        PluginCommand command = Objects.requireNonNull(getCommand("flaggame"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        Bukkit.getPluginManager().registerEvents(game, this);

        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            try {
                images.removeStaleImages();
                int countries = game.refreshCatalog();
                getLogger().info("Loaded " + countries + " flags from FlagCDN or its local cache.");
            } catch (ReflectiveOperationException exception) {
                getLogger().warning("Could not clean up stale FlagGame images: " + exception.getMessage());
            } catch (Exception exception) {
                getLogger().warning("Could not refresh FlagCDN's country list; using the offline locale list: "
                        + exception.getMessage());
            } finally {
                Bukkit.getScheduler().runTask(this, game::markReady);
            }
        }, 60L);
        getLogger().info("FlagGame enabled with " + new CountryCatalog().countries().size() + " flags.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
    }

    private void migrateLegacyImageSize() {
        if (getConfig().getInt("image.width", 100) == 100
                && getConfig().getInt("image.height", 100) == 100) {
            getConfig().set("image.width", 384);
            getConfig().set("image.height", 256);
            saveConfig();
            getLogger().info("Updated the legacy 100x100 flag size to a 384x256 (3x2 map) display.");
        }
    }
}
