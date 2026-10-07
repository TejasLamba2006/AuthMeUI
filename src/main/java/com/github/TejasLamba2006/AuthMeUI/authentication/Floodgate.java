package com.github.TejasLamba2006.AuthMeUI.authentication;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ponytail reflective Floodgate (Geyser) lookup (feature O), ported verbatim
 * from AuthMeBia's {@code listeners/floodgate/Floodgate.java}. Floodgate is
 * optional at runtime, so every call is reflected — no compile-time dep, and
 * every method degrades to false/null when Floodgate is absent. Methods are
 * cached behind double-checked locking; {@link #reset()} is exposed for tests.
 *
 * <p>Used by {@link AuthenticationBridge} to detect bedrock players and resolve
 * their linked Java account UUID (LINK mode). AuthMeBia's parallel class also
 * feeds a per-player {@code BedrockCfg}; AuthMeUI applies the bedrock dialog
 * width override directly in {@link SettingsManager} instead (no separate Cfg
 * subclass — fewer moving parts).
 */
public final class Floodgate {

    private static volatile boolean checked = false;
    private static volatile Object api = null;
    private static volatile Method isFloodgatePlayerMethod = null;
    private static volatile Method getPlayerMethod = null;
    private static volatile Method getLinkedPlayerMethod = null;
    private static volatile Method linkedJavaUsernameMethod = null;
    private static volatile Method linkedJavaUniqueIdMethod = null;

    private Floodgate() {}

    public static void reset() {
        synchronized (Floodgate.class) {
            checked = false;
            api = null;
            isFloodgatePlayerMethod = null;
            getPlayerMethod = null;
            getLinkedPlayerMethod = null;
            linkedJavaUsernameMethod = null;
            linkedJavaUniqueIdMethod = null;
        }
    }

    public static boolean isAvailable() {
        ensureLookup();
        return api != null;
    }

    public static boolean isFloodgatePlayer(UUID uuid) {
        ensureLookup();
        if (api == null || isFloodgatePlayerMethod == null || uuid == null) return false;
        try {
            Object result = isFloodgatePlayerMethod.invoke(api, uuid);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            return false;
        }
    }

    public static UUID getLinkedJavaUuid(UUID bedrockUuid) {
        ensureLookup();
        if (api == null || getPlayerMethod == null || getLinkedPlayerMethod == null
                || linkedJavaUniqueIdMethod == null || bedrockUuid == null) {
            return null;
        }
        try {
            Object floodgatePlayer = getPlayerMethod.invoke(api, bedrockUuid);
            if (floodgatePlayer == null) return null;
            Object linkedPlayer = getLinkedPlayerMethod.invoke(floodgatePlayer);
            if (linkedPlayer == null) return null;
            Object result = linkedJavaUniqueIdMethod.invoke(linkedPlayer);
            return result instanceof UUID u ? u : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureLookup() {
        if (checked) return;
        synchronized (Floodgate.class) {
            if (checked) return;
            checked = true;
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstance = apiClass.getMethod("getInstance");
                api = getInstance.invoke(null);

                isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                getPlayerMethod = apiClass.getMethod("getPlayer", UUID.class);

                Class<?> floodgatePlayerClass = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
                getLinkedPlayerMethod = floodgatePlayerClass.getMethod("getLinkedPlayer");

                Class<?> linkedPlayerClass = Class.forName("org.geysermc.floodgate.util.LinkedPlayer");
                linkedJavaUniqueIdMethod = linkedPlayerClass.getMethod("getJavaUniqueId");
                linkedJavaUsernameMethod = linkedPlayerClass.getMethod("getJavaUsername");
            } catch (Throwable t) {
                api = null;
                isFloodgatePlayerMethod = null;
                getPlayerMethod = null;
                getLinkedPlayerMethod = null;
                linkedJavaUniqueIdMethod = null;
                linkedJavaUsernameMethod = null;
            }
        }
    }
}
