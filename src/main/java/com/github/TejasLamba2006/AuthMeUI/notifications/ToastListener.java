package com.github.TejasLamba2006.AuthMeUI.notifications;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.RegisterEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponytail: M — ported from AuthMeBia's {@code notifications/ToastListener}.
 * Fires advancement-based toast popups on first_register / first_login /
 * first_message / first_advancement triggers. Advancements are registered
 * on first need via {@link org.bukkit.Unsafe#loadAdvancement} (deprecated
 * but the only Paper API for runtime advancement registration), criteria are
 * awarded to show the toast, then revoked on a delay so the toast can fire
 * again next time. Per-session one-shot sets guard first_message and
 * first_advancement so they fire at most once per session.
 *
 * <p>Known limitation (inherited from AuthMeBia): changing the title/icon/
 * content of an already-registered toast requires a full server restart,
 * because advancement registry entries are immutable once loaded.
 */
@SuppressWarnings("deprecation")
public class ToastListener implements Listener {

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final SettingsManager settings;
    private final ToastStore store;

    // Advancement keys we've registered this session, so we don't try to
    // re-register (loadAdvancement throws on duplicates).
    private final Set<NamespacedKey> registeredAdvancementKeys = ConcurrentHashMap.newKeySet();

    // Per-session one-shot sets: first_message and first_advancement fire
    // at most once per session per player.
    private final Set<java.util.UUID> messagedThisSession = ConcurrentHashMap.newKeySet();
    private final Set<java.util.UUID> advancementThisSession = ConcurrentHashMap.newKeySet();

    public ToastListener(AuthMeUIPlugin plugin, AuthenticationBridge authBridge,
            SettingsManager settings, ToastStore store) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.settings = settings;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthMeRegister(RegisterEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        fireAll(player, Toast.Check.FIRST_REGISTER);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthMeLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        fireAll(player, Toast.Check.FIRST_LOGIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // ponytail: M — first_advancement toasts are fired by the
        // PlayerAdvancementDoneEvent handler; first_message by the first
        // chat message. Join itself has no toast trigger, but we clear
        // session sets on quit (see onPlayerQuit), so nothing to do here.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (advancementThisSession.contains(player.getUniqueId())) return;
        // Only fire for OUR registered toast advancements, not vanilla ones.
        if (!registeredAdvancementKeys.contains(event.getAdvancement().getKey())) return;
        advancementThisSession.add(player.getUniqueId());
        fireAll(player, Toast.Check.FIRST_ADVANCEMENT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // ponytail: per-session one-shots reset on quit so the next session
        // can fire them again (matches AuthMeBia's quit-remove behavior).
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        messagedThisSession.remove(uuid);
        advancementThisSession.remove(uuid);
    }

    /**
     * Fire every configured toast matching {@code check} that this player
     * hasn't already seen. Skips: mismatched check, already-shown (flat-file
     * store for register/login, session sets for message/advancement).
     */
    private void fireAll(Player player, Toast.Check check) {
        List<Toast> toasts = settings.getToasts();
        for (Toast toast : toasts) {
            if (toast.check() != check) continue;
            if (store.hasShown(player.getUniqueId(), toast.name())) continue;
            store.markShown(player.getUniqueId(), toast.name());
            showToast(player, toast);
        }
    }

    /**
     * Play the toast's sound (if any), register its advancement on first
     * use, award all remaining criteria to pop the toast, then revoke on a
     * delay so the toast can re-fire later. Revokes are scheduled via
     * {@code player.getScheduler().runDelayed} (Folia-safe; falls back to the
     * same call on Paper — Paper's PlayerScheduler is the legacy scheduler).
     */
    private void showToast(Player player, Toast toast) {
        // ponytail: M — re-resolve {player}/placeholders with the firing player.
        toast = settings.resolveToastForPlayer(toast, player);
        if (toast.sound() != null && !toast.sound().isBlank()) {
            try {
                // ponytail: M — use the String overload of playSound so
                // "minecraft:entity.player.levelup" keys work even when they
                // aren't backed by a Bukkit Sound enum value (Paper resolves
                // the namespaced key at runtime). Location is the player's
                // own location (self-played).
                player.playSound(
                        player.getLocation(),
                        toast.sound().trim().split("\\s+")[0],
                        extractVolume(toast.sound()),
                        extractPitch(toast.sound()));
            } catch (IllegalArgumentException ignored) {
                // ponytail: sound parse failure shouldn't kill the toast.
            }
        }

        Advancement advancement = getOrCreateAdvancement(toast);
        if (advancement == null) return;

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        java.util.Collection<String> remaining = progress.getRemainingCriteria();
        for (String criterion : remaining) {
            progress.awardCriteria(criterion);
        }
        // If nothing was remaining (toast already shown once this tick), revoke
        // leftovers so a re-award has something to award next time.
        if (remaining.isEmpty()) {
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
            // Re-award now that we revoked.
            for (String criterion : advancement.getCriteria()) {
                progress.awardCriteria(criterion);
            }
        }

        // Revoke after the configured delay so the toast can fire again.
        int revokeDelayTicks = Math.max(1, toast.delaySeconds()) * 20;
        player.getScheduler().runDelayed(plugin, scheduledTask -> {
            if (!player.isOnline()) return;
            AdvancementProgress p = player.getAdvancementProgress(advancement);
            for (String criterion : p.getAwardedCriteria()) {
                p.revokeCriteria(criterion);
            }
        }, null, revokeDelayTicks);
    }

    /**
     * Get or register the advancement backing this toast. Uses
     * {@link Bukkit#getUnsafe()}#loadAdvancement} (deprecated but the only
     * runtime registration API). Dedups via {@link #registeredAdvancementKeys}.
     */
    private Advancement getOrCreateAdvancement(Toast toast) {
        String keyStr = "authmeui_toast_" + toast.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        NamespacedKey key = new NamespacedKey(plugin, keyStr);
        if (registeredAdvancementKeys.contains(key)) {
            return Bukkit.getAdvancement(key);
        }
        try {
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, buildAdvancementJson(toast));
            registeredAdvancementKeys.add(key);
            return advancement;
        } catch (IllegalArgumentException dup) {
            // Already registered (e.g. reload within same session) — just fetch.
            registeredAdvancementKeys.add(key);
            return Bukkit.getAdvancement(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register toast advancement '" + toast.name() + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Build the advancement JSON for a toast. Mirrors AuthMeBia's
     * buildAdvancementJson exactly: one criterion "trigger" with
     * minecraft:tick, requirements {@code [["trigger"]]}, and a display
     * with icon/title/description/frame=..., show_toast=true, announce=false,
     * hidden=true.
     */
    private String buildAdvancementJson(Toast toast) {
        String icon = toast.icon() != null && !toast.icon().isBlank()
                ? toast.icon()
                : "minecraft:player_head";
        String title = plainText(toast.title());
        String description = plainText(toast.content());
        String frame = toast.frame().jsonValue();

        return "{\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:tick\"}},"
                + "\"requirements\":[[\"trigger\"]],"
                + "\"display\":{\"icon\":{\"id\":\"" + escape(icon) + "\"},"
                + "\"title\":{\"text\":\"" + escape(title) + "\"},"
                + "\"description\":{\"text\":\"" + escape(description) + "\"},"
                + "\"frame\":\"" + frame + "\","
                + "\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true}},"
                + "\"rewards\":{}}";
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static String plainText(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // --- sound string parsing ("minecraft:entity.player.levelup 0.6 1.0") ---

    private static float extractVolume(String sound) {
        String[] parts = sound.trim().split("\\s+");
        if (parts.length >= 2) {
            try { return Float.parseFloat(parts[1]); } catch (NumberFormatException ignored) {}
        }
        return 1.0f;
    }

    private static float extractPitch(String sound) {
        String[] parts = sound.trim().split("\\s+");
        if (parts.length >= 3) {
            try { return Float.parseFloat(parts[2]); } catch (NumberFormatException ignored) {}
        }
        return 1.0f;
    }
}
