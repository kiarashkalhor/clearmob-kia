package com.clearmob.clearmob;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClearMobPlugin extends JavaPlugin implements CommandExecutor {

    private boolean enabled = true;
    private int clearInterval;
    private Set<EntityType> entitiesToClear;
    private Set<EntityType> entitiesToKeepNamed;
    private String warningBeforeClear1;
    private String warningBeforeClear2;
    private String warningAfterClear;
    private int warningInterval1;
    private int warningInterval2;

    private long lastWarning1Time;
    private long lastWarning2Time;
    private long startTime;

    @Override
    public void onEnable() {
        // Save the default config file
        this.saveDefaultConfig();
        this.getCommand("clearmob").setExecutor(this);

        // Load the config file
        loadConfig();

        if (enabled) {
            startTasks();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Mob Killer Plugin has been disabled!");
    }

    private void loadConfig() {
        this.clearInterval = this.getConfig().getInt("clear-interval");
        getLogger().info("Clear Interval: " + clearInterval);

        List<String> entityNames = this.getConfig().getStringList("entities");
        this.entitiesToClear = new HashSet<>();
        this.entitiesToKeepNamed = new HashSet<>();

        this.warningBeforeClear1 = this.getConfig().getString("warnings.before-clear-1");
        this.warningBeforeClear2 = this.getConfig().getString("warnings.before-clear-2");
        this.warningAfterClear = this.getConfig().getString("warnings.after-clear");
        this.warningInterval1 = this.getConfig().getInt("warning-interval-1");
        this.warningInterval2 = this.getConfig().getInt("warning-interval-2");

        getLogger().info("Warning Before Clear 1: " + warningBeforeClear1);
        getLogger().info("Warning Before Clear 2: " + warningBeforeClear2);
        getLogger().info("Warning After Clear: " + warningAfterClear);
        getLogger().info("Warning Interval 1: " + warningInterval1);
        getLogger().info("Warning Interval 2: " + warningInterval2);

        boolean allEntities = false;
        for (String entry : entityNames) {
            if (entry.equals("ALL_ENTITIES")) {
                allEntities = true;
                break;
            } else if (entry.startsWith("!hasname ")) {
                String entityName = entry.substring("!hasname ".length()).trim();
                try {
                    EntityType type = EntityType.valueOf(entityName);
                    entitiesToKeepNamed.add(type);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Unknown entity type for keeping named: " + entityName);
                }
            } else {
                try {
                    EntityType type = EntityType.valueOf(entry);
                    entitiesToClear.add(type);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Unknown entity type: " + entry);
                }
            }
        }

        if (allEntities) {
            for (EntityType type : EntityType.values()) {
                if (type.isAlive() && !entitiesToKeepNamed.contains(type)) {
                    entitiesToClear.add(type);
                }
            }
        }

        // Initialize start time for the first clear
        startTime = System.currentTimeMillis();
        getLogger().info("Start Time Initialized: " + startTime);
    }


    private BukkitRunnable clearTask;
    private BukkitRunnable warningTask;

    private void startTasks() {
        if (clearTask != null) {
            clearTask.cancel(); // Cancel existing task if any
        }
        if (warningTask != null) {
            warningTask.cancel(); // Cancel existing task if any
        }

        warningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) {
                    cancel(); // Stop the task if disabled
                    return;
                }
                checkAndSendWarnings();
            }
        };
        warningTask.runTaskTimer(this, 0L, 20L); // Check every second

        clearTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) {
                    cancel(); // Stop the task if disabled
                    return;
                }
                clearMobs();
            }
        };
        clearTask.runTaskTimer(this, clearInterval * 20L, clearInterval * 20L); // Clear mobs at the interval
    }



    private void checkAndSendWarnings() {
        long currentTime = System.currentTimeMillis() / 1000; // Convert milliseconds to seconds
        long elapsedTime = (currentTime - (startTime / 1000)); // Time elapsed since last clear
        long timeRemaining = (clearInterval - (elapsedTime % clearInterval));

        String warningBeforeClear1Message = ChatColor.translateAlternateColorCodes('&', warningBeforeClear1);
        String warningBeforeClear2Message = ChatColor.translateAlternateColorCodes('&', warningBeforeClear2);

        if (timeRemaining <= warningInterval2 && timeRemaining > warningInterval1) {
            if ((currentTime - lastWarning2Time) >= warningInterval2) {
                Bukkit.broadcastMessage(warningBeforeClear2Message.replace("%seconds%", String.valueOf(warningInterval2)));
                lastWarning2Time = currentTime;
            }
        } else if (timeRemaining <= warningInterval1) {
            if ((currentTime - lastWarning1Time) >= warningInterval1) {
                Bukkit.broadcastMessage(warningBeforeClear1Message.replace("%seconds%", String.valueOf(warningInterval1)));
                lastWarning1Time = currentTime;
            }
        }
    }


    private void clearMobs() {
        if (!enabled) return;

        int removedMobsCount = 0;

        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) {
            if (entitiesToClear.contains(entity.getType()) && !entitiesToKeepNamed.contains(entity.getType())) {
                if (entity.getCustomName() == null || !entitiesToKeepNamed.contains(entity.getType())) {
                    entity.remove();
                    removedMobsCount++;
                }
            }
        }

        // Get the configurable after-clear message with the number of removed mobs
        String afterClearMessage = this.getConfig().getString("warnings.after-clear");
        String finalMessage = afterClearMessage.replace("%count%", String.valueOf(removedMobsCount));

        // Broadcast the message
        Bukkit.broadcastMessage(finalMessage);

        startTime = System.currentTimeMillis(); // Reset the start time for the next cycle
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("clearmob")) {
            if (args.length == 0) {
                return false;
            }

            if (args[0].equalsIgnoreCase("enable")) {
                if (sender.hasPermission("clearmob.admin")) {
                    enabled = true;
                    sender.sendMessage("ClearMobPlugin has been enabled.");
                    startTasks(); // Restart tasks if enabled
                    return true;
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("disable")) {
                if (sender.hasPermission("clearmob.admin")) {
                    enabled = false;
                    sender.sendMessage("ClearMobPlugin has been disabled.");
                    return true;
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("clearmob.admin")) {
                    reloadConfig();
                    loadConfig();
                    if (enabled) {
                        startTasks(); // Restart tasks if enabled
                    }
                    sender.sendMessage("Configuration has been reloaded.");
                    return true;
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }
}
