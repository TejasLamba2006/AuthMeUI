package com.github.TejasLamba2006.AuthMeUI.dialogs;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether the premium password-set dialog (feature K) has already been
 * shown to a player this session. AuthMeBia re-prompts on every premium
 * auto-login because it relies on an internal placeholder password that the
 * dialog overwrites; AuthMeUI has no placeholder mechanism, so we prompt once
 * per session to avoid re-showing the dialog on every reconnect of a verified
 * premium player who has already set a real password.
 *
 * <p>ponytail: in-memory set, no persistence. A restart re-prompts once,
 * which is acceptable (the player can just close it). If re-prompting after
 * restart becomes annoying, persist a "done" file like RecoverStore.
 */
public final class PremiumSetTracker {

    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();

    public boolean shouldShow(UUID uuid) {
        return uuid != null && !shown.contains(uuid);
    }

    public void markShown(UUID uuid) {
        if (uuid != null) {
            shown.add(uuid);
        }
    }
}
