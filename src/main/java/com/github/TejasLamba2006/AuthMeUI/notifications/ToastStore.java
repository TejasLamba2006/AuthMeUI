package com.github.TejasLamba2006.AuthMeUI.notifications;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponytail: M — flat-file persistence for "has this player already seen
 * toast X" (so first_register/first_login toasts fire once ever, not once
 * per session). AuthMeBia used its own PlayerDataStore for this; AuthMeUI has
 * no such store, so we clone the ScreenDismissStore pattern: one file per
 * player under {@code plugins/AuthMeUI/toasts_shown/<uuid>.txt}, lowercased
 * toast names one per line, in-memory cache fronting reads. Writes are rare
 * (once per toast per player), so flat-file is plenty.
 */
public class ToastStore {

    private final AuthMeUIPlugin plugin;
    private final Path dir;
    private final ConcurrentHashMap<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public ToastStore(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("toasts_shown");
    }

    public boolean hasShown(UUID uuid, String toastName) {
        if (toastName == null) return false;
        ensureLoaded(uuid);
        Set<String> set = cache.get(uuid);
        return set != null && set.contains(toastName.toLowerCase(Locale.ROOT));
    }

    public void markShown(UUID uuid, String toastName) {
        if (toastName == null) return;
        cache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(toastName.toLowerCase(Locale.ROOT));
        save(uuid);
    }

    private void ensureLoaded(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        try {
            Path f = file(uuid);
            if (Files.exists(f)) {
                List<String> lines = Files.readAllLines(f);
                Set<String> set = new HashSet<>();
                for (String l : lines) {
                    if (!l.isBlank()) set.add(l.trim().toLowerCase(Locale.ROOT));
                }
                cache.put(uuid, set);
            } else {
                cache.putIfAbsent(uuid, new HashSet<>());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read toast store for " + uuid + ": " + e.getMessage());
            cache.putIfAbsent(uuid, new HashSet<>());
        }
    }

    private void save(UUID uuid) {
        try {
            Files.createDirectories(dir);
            Set<String> set = cache.get(uuid);
            if (set == null || set.isEmpty()) {
                Files.deleteIfExists(file(uuid));
            } else {
                Files.write(file(uuid), set);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write toast store for " + uuid + ": " + e.getMessage());
        }
    }

    private Path file(UUID uuid) {
        return dir.resolve(uuid + ".txt");
    }
}
