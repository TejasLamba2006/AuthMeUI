package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat-file store for the admin-forced password-reset flag (feature L).
 * Mirrors AuthMeBia's {@code RecoverStore}: an admin flags a player via
 * {@code /authmeui reset <player>}; the flag persists across restarts and
 * is cleared once the player completes the reset dialog.
 *
 * <p>ponytail: one file per player under {@code plugins/AuthMeUI/recover/<uuid>.yml}.
 * A ConcurrentHashMap mirrors the on-disk state so the hot read path
 * ({@code isFlagged}) is lock-free; writes take the file lock.
 */
public final class RecoverStore {

    private final AuthMeUIPlugin plugin;
    private final File dataFolder;
    private final Set<UUID> flagged = ConcurrentHashMap.newKeySet();

    public RecoverStore(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "recover");
        load();
    }

    private void load() {
        if (!dataFolder.isDirectory()) {
            return;
        }
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String stem = file.getName().replace(".yml", "");
                UUID uuid = UUID.fromString(stem);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                if (yaml.getBoolean("recover", false)) {
                    flagged.add(uuid);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping recover file with invalid UUID name: " + file.getName());
            }
        }
    }

    public boolean isFlagged(UUID uuid) {
        return uuid != null && flagged.contains(uuid);
    }

    public boolean flag(UUID uuid, String name) {
        if (uuid == null) {
            return false;
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create recover data folder: " + dataFolder);
            return false;
        }
        File file = fileFor(uuid);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (name != null) {
            yaml.set("name", name);
        }
        yaml.set("uuid", uuid.toString());
        yaml.set("recover", true);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to flag " + uuid + " for recovery: " + e.getMessage());
            return false;
        }
        flagged.add(uuid);
        return true;
    }

    public void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        flagged.remove(uuid);
        File file = fileFor(uuid);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("recover", null);
        // ponytail: keep the file if it still holds other keys, else delete.
        if (yaml.getKeys(false).isEmpty()) {
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete recover file: " + file);
            }
        } else {
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save recover file: " + e.getMessage());
            }
        }
    }

    private File fileFor(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }
}
