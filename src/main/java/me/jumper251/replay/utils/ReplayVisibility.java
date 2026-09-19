package me.jumper251.replay.utils;

import me.jumper251.replay.ReplaySystem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayVisibility {
    private static final File FILE = new File(ReplaySystem.getInstance().getDataFolder(), "visibilitys.yml");
    private static final Map<String, Boolean> VALUES = new ConcurrentHashMap<>();
    private static boolean loaded;

    private ReplayVisibility() { }

    public static String key(String creator, String replayName) {
        return creator + "-" + replayName;
    }

    private static synchronized void load() {
        if (loaded) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(FILE);
        for (String key : config.getKeys(false)) {
            VALUES.put(key, config.getBoolean(key, false));
        }
        loaded = true;
    }

    public static boolean isPublic(String storageKey) {
        load();
        return VALUES.getOrDefault(storageKey, false);
    }

    public static void setPublic(String storageKey, boolean visible) {
        load();
        VALUES.put(storageKey, visible);
        save();
    }

    public static void remove(String storageKey) {
        load();
        VALUES.remove(storageKey);
        save();
    }

    private static synchronized void save() {
        FileConfiguration config = new YamlConfiguration();
        VALUES.forEach(config::set);
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            config.save(FILE);
        } catch (IOException exception) {
            ReplaySystem.getInstance().getLogger().warning("Could not save visibilitys.yml: " + exception.getMessage());
        }
    }
}
