package com.mauli.timescheduler;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimeSchedulerPlugin extends JavaPlugin {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Map<String, ScheduledCommand> scheduled = new ConcurrentHashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;
    private int taskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        loadSchedulesFromConfig();
        startTicker();
        getLogger().info("TimeScheduler enabled. Loaded schedules: " + scheduled.size());
    }

    @Override
    public void onDisable() {
        stopTicker();
        saveData();
        scheduled.clear();
    }

    // ----- Command: /timescheduler reload -----
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("timescheduler.reload")) {
                sender.sendMessage("§cKeine Berechtigung.");
                return true;
            }
            stopTicker();
            reloadConfig();
            loadSchedulesFromConfig();
            startTicker();
            sender.sendMessage("§aTimeScheduler neu geladen. Geladene Termine: " + scheduled.size());
            return true;
        }
        sender.sendMessage("§eVerwendung: /timescheduler reload");
        return true;
    }

    // ----- Scheduler-Loop -----
    private void startTicker() {
        int intervalSec = Math.max(1, getConfig().getInt("check-interval-seconds", 5));
        long intervalTicks = intervalSec * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tick, 20L, intervalTicks);
    }

    private void stopTicker() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        for (ScheduledCommand sc : new ArrayList<>(scheduled.values())) {
            try {
                if (isExecuted(sc.getId()) && sc.getRepeat() == ScheduledCommand.Repeat.NONE) {
                    continue;
                }
                if (sc.getNextRunAt() == null) {
                    sc.computeNextRun(now);
                }
                if (sc.getNextRunAt() != null && !sc.getNextRunAt().isAfter(now)) {
                    runScheduled(sc);
                    if (sc.getRepeat() == ScheduledCommand.Repeat.NONE) {
                        markExecuted(sc.getId());
                        sc.setNextRunAt(null);
                    } else {
                        sc.computeNextRun(now.plusSeconds(1));
                    }
                    saveData();
                }
            } catch (Exception ex) {
                getLogger().warning("Fehler bei Termin '" + sc.getId() + "': " + ex.getMessage());
            }
        }
    }

    private void runScheduled(ScheduledCommand sc) {
        String cmd = sc.getCommand();
        String runAs = sc.getRunAs();

        if (runAs != null && runAs.toUpperCase(Locale.ROOT).startsWith("PLAYER:")) {
            String playerName = runAs.substring("PLAYER:".length());
            cmd = cmd.replace("%player%", playerName);
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                Bukkit.dispatchCommand(p, cmd);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        getLogger().info("Ausgeführt [" + sc.getId() + "] -> /" + cmd);
    }

    // ----- Config laden -----
    private void loadSchedulesFromConfig() {
        scheduled.clear();
        FileConfiguration cfg = getConfig();

        String defaultTz = cfg.getString("default-timezone", ZoneId.systemDefault().getId());
        ZoneId defaultZone;
        try {
            defaultZone = ZoneId.of(defaultTz);
        } catch (Exception e) {
            defaultZone = ZoneId.systemDefault();
            getLogger().warning("Ungültige default-timezone in config.yml. Verwende System: " + defaultZone);
        }

        List<Map<?, ?>> list = cfg.getMapList("schedules");
        for (Map<?, ?> raw : list) {
            ConfigurationSection section = mapToSection(raw);
            String id = section.getString("id");
            String dt = section.getString("datetime");
            String tz = section.getString("timezone", defaultZone.getId());
            String command = section.getString("command");
            String runAs = section.getString("run-as", "CONSOLE");
            String repeatStr = section.getString("repeat", "NONE");

            if (id == null || id.isEmpty() || dt == null || dt.isEmpty() || command == null || command.isEmpty()) {
                getLogger().warning("Überspringe ungültigen Eintrag (id/datetime/command fehlt).");
                continue;
            }

            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(tz);
            } catch (Exception e) {
                zoneId = defaultZone;
                getLogger().warning("Ungültige timezone bei '" + id + "'. Verwende: " + zoneId);
            }

            LocalDateTime ldt;
            try {
                ldt = LocalDateTime.parse(dt, FORMAT);
            } catch (Exception e) {
                getLogger().warning("Ungültiges datetime-Format bei '" + id + "'. Erwartet: yyyy-MM-dd HH:mm");
                continue;
            }

            ScheduledCommand.Repeat repeat = ScheduledCommand.Repeat.fromString(repeatStr);

            ScheduledCommand sc = new ScheduledCommand(
                    id, command, runAs, ldt, zoneId, repeat
            );

            if (repeat == ScheduledCommand.Repeat.NONE && isExecuted(id)) {
                sc.setNextRunAt(null);
            }

            scheduled.put(id, sc);
        }
    }

    private ConfigurationSection mapToSection(Map<?, ?> map) {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) yc.set(e.getKey().toString(), e.getValue());
        }
        return yc;
    }

    // ----- Persistenz: data.yml (ausgeführte IDs) -----
    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Konnte data.yml nicht erstellen", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataConfig.isList("executed")) {
            dataConfig.set("executed", new ArrayList<String>());
            saveData();
        }
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Konnte data.yml nicht speichern: " + e.getMessage());
        }
    }

    private boolean isExecuted(String id) {
        List<String> list = dataConfig.getStringList("executed");
        return list.contains(id);
    }

    private void markExecuted(String id) {
        List<String> list = new ArrayList<>(dataConfig.getStringList("executed"));
        if (!list.contains(id)) {
            list.add(id);
            dataConfig.set("executed", list);
        }
    }
}
