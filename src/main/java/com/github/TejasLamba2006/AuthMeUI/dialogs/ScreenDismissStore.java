package com.github.TejasLamba2006.AuthMeUI.dialogs;

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
 * Per-player "dismiss forever" store for custom screens (feature J). When a
 * player ticks a screen's {@code dismiss_forever} checkbox and the screen's
 * {@code checkbox_action} is {@code close}, the screen id is recorded here
 * and auto-triggered (postjoin/prejoin) screens skip the player from then on.
 *
 * <p>ponytail: flat-file persistence (one file per player under
 * {@code plugins/AuthMeUI/dismissed/<uuid>.txt}). Cheaper than a DB for a
 * feature that writes rarely; in-memory cache fronts reads. If this ever
 * becomes hot, swap to a single YAML keyed by uuid.
 */
public class ScreenDismissStore {

    private final AuthMeUIPlugin plugin;
    private final Path dir;
    // uuid -> lowercased screen ids the player has dismissed
    private final ConcurrentHashMap<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public ScreenDismissStore(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("dismissed");
    }

    public boolean isDismissed(UUID uuid, String screenId) {
        if (screenId == null) return false;
        Set<String> set = cache.get(uuid);
        return set != null && set.contains(screenId.toLowerCase(Locale.ROOT));
    }

    public void markDismissed(UUID uuid, String screenId) {
        if (screenId == null) return;
        cache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(screenId.toLowerCase(Locale.ROOT));
        save(uuid);
    }

    public void clear(UUID uuid, String screenId) {
        if (screenId == null) return;
        Set<String> set = cache.get(uuid);
        if (set != null) {
            set.remove(screenId.toLowerCase(Locale.ROOT));
            save(uuid);
        }
    }

    public void clearAll(UUID uuid) {
        cache.remove(uuid);
        try {
            Files.deleteIfExists(file(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete dismiss store for " + uuid + ": " + e.getMessage());
        }
    }

    private void load(UUID uuid) {
        try {
            Path f = file(uuid);
            if (Files.exists(f)) {
                List<String> lines = Files.readAllLines(f);
                Set<String> set = new HashSet<>();
                for (String l : lines) {
                    if (!l.isBlank()) set.add(l.trim().toLowerCase(Locale.ROOT));
                }
                cache.put(uuid, set);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read dismiss store for " + uuid + ": " + e.getMessage());
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
            plugin.getLogger().warning("Failed to write dismiss store for " + uuid + ": " + e.getMessage());
        }
    }

    private Path file(UUID uuid) {
        return dir.resolve(uuid + ".txt");
    }
}
