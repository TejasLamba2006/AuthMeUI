package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles authentication dialogs during the configuration phase (before the
 * player joins the server).
 * This allows for blocking authentication where players must authenticate
 * before being allowed to join.
 */
public class ConfigurationPhaseListener implements Listener {

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;
    private final SettingsManager settings;
    private final boolean fastLoginDetected;

    /**
     * Tracks pending authentication responses for players in the configuration
     * phase.
     */
    private final Map<UUID, CompletableFuture<Boolean>> pendingAuthentications = new ConcurrentHashMap<>();

    /**
     * Tracks whether a player is registered (for showing the correct dialog).
     */
    private final Map<UUID, Boolean> playerRegistrationStatus = new ConcurrentHashMap<>();

    /**
     * Tracks players who successfully authenticated during the configuration phase
     * and need to be force-logged in when they finish joining.
     */
    private final Set<UUID> preAuthenticatedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Tracks player IPs discovered during async pre-login for session checks.
     */
    private final Map<UUID, String> connectionIps = new ConcurrentHashMap<>();

    /**
     * Tracks registered players whose login checks are deferred until post-join.
     */
    private final Set<UUID> deferredPostJoinChecks = ConcurrentHashMap.newKeySet();

    /**
     * Tracks players who explicitly cancelled authentication via dialog action.
     */
    private final Set<UUID> cancelledAuthentications = ConcurrentHashMap.newKeySet();

    public ConfigurationPhaseListener(
            AuthMeUIPlugin plugin,
            AuthenticationBridge authBridge,
            DialogManager dialogManager,
            SettingsManager settings) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
        this.settings = settings;
        this.fastLoginDetected = plugin.getServer().getPluginManager().isPluginEnabled("FastLogin");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uniqueId = event.getUniqueId();
        if (uniqueId == null || event.getAddress() == null) {
            return;
        }

        connectionIps.put(uniqueId, event.getAddress().getHostAddress());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!settings.useConfigurationPhase()) {
            return;
        }

        if (!authBridge.isConnected()) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uniqueId = connection.getProfile().getId();

        if (uniqueId == null) {
            plugin.getLogger().warning("Player with null UUID attempted to connect");
            return;
        }

        String playerName = connection.getProfile().getName();
        if (playerName == null) {
            plugin.getLogger().warning("Player with null name attempted to connect");
            return;
        }

        // Check if player has bypass permission (we can't check permissions during
        // config phase,
        // so we rely on the post-join check if they have the bypass)
        // For now, we proceed with authentication for all players

        boolean isRegistered = authBridge.isPlayerRegistered(playerName);
        playerRegistrationStatus.put(uniqueId, isRegistered);

        if (isRegistered && shouldDeferToPostJoin(uniqueId, playerName)) {
            playerRegistrationStatus.remove(uniqueId);
            deferredPostJoinChecks.add(uniqueId);
            return;
        }

        // Create a completable future for this authentication attempt
        CompletableFuture<Boolean> authFuture = new CompletableFuture<>();
        int timeout = settings.getConfigurationPhaseTimeout();
        authFuture.completeOnTimeout(false, timeout, TimeUnit.SECONDS);

        pendingAuthentications.put(uniqueId, authFuture);

        Audience audience = connection.getAudience();

        // ponytail: K+L — an admin-forced reset flag takes priority over the
        // normal login/register/captcha flow, even in config phase. The
        // RECOVER_SUBMIT handler clears the flag + completes auth on success.
        com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverStore recoverStore =
                dialogManager.getRecoverStore();
        if (isRegistered && recoverStore != null && recoverStore.isFlagged(uniqueId)) {
            dialogManager.markRecoverMode(uniqueId,
                    DialogManager.RecoverMode.ADMIN_RESET);
            audience.showDialog(dialogManager.createRecoverDialogForAudience(false));
            // Wait for the recover submit to complete authentication.
            boolean authenticated = authFuture.join();
            pendingAuthentications.remove(uniqueId);
            playerRegistrationStatus.remove(uniqueId);
            if (!authenticated) {
                connection.disconnect(settings.getMessage("login.cancelled",
                        "<yellow>Authentication cancelled.</yellow>"));
            } else {
                showPrejoinScreens(connection, audience, uniqueId, playerName);
            }
            return;
        }

        // Show the appropriate dialog
        if (isRegistered) {
            // ponytail: captcha gate for trigger==0 (always-show) on
            // config-phase login. trigger>0 is handled in the listener's
            // login-failure branch. DialogManager owns the same gate for
            // the in-game path, but config-phase presentation happens here
            // directly, so we mirror the check.
            com.github.TejasLamba2006.AuthMeUI.authentication.Captcha captcha = dialogManager.getCaptcha();
            if (settings.isCaptchaEnabled()
                    && settings.getCaptchaFailuresBeforeTrigger() == 0
                    && captcha != null
                    && !captcha.isTrusted(uniqueId)) {
                String code = captcha.generateFor(uniqueId, settings.getCaptchaCodeLength());
                audience.showDialog(dialogManager.createCaptchaDialogForAudience(code, null));
            } else {
                // ponytail: delegate to DialogManager so PIN/slider modes are
                // gated consistently with the in-game path.
                dialogManager.presentLoginDialogForAudience(audience, playerName);
            }
        } else {
            if (settings.isRulesDialogEnabled()) {
                audience.showDialog(dialogManager.createRulesDialogForAudience(playerName));
            } else {
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName));
            }
        }

        // Wait for authentication to complete
        boolean authenticated = authFuture.join();

        // Clean up
        pendingAuthentications.remove(uniqueId);
        playerRegistrationStatus.remove(uniqueId);

        if (!authenticated) {
            Component disconnectMessage = cancelledAuthentications.remove(uniqueId)
                    ? settings.getMessage("login.cancelled", "<yellow>Authentication cancelled.</yellow>")
                    : Component.text("Authentication timed out or failed. Please reconnect and try again.");
            connection.disconnect(disconnectMessage);
            return;
        }

        // ponytail: feature J — show PREJOIN-trigger custom screens after
        // successful config-phase auth, before the player enters the game.
        // Mirrors AuthMeBia's showPrejoinScreensBlocking: iterate in config
        // order, skip disabled/dismissed, show the first match.
        showPrejoinScreens(connection, audience, uniqueId, playerName);
    }

    /**
     * Show the first enabled, non-dismissed, PREJOIN-trigger custom screen
     * to a config-phase audience (feature J).
     */
    private void showPrejoinScreens(
            PlayerConfigurationConnection connection,
            Audience audience,
            UUID uniqueId,
            String playerName) {
        java.util.List<com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen> screens =
                settings.getCustomScreens();
        for (int i = 0; i < screens.size(); i++) {
            com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen screen = screens.get(i);
            if (!screen.enabled()) continue;
            if (screen.trigger() != com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen.Trigger.PREJOIN) {
                continue;
            }
            com.github.TejasLamba2006.AuthMeUI.dialogs.ScreenDismissStore store =
                    dialogManager.getScreenDismissStore();
            if (store != null
                    && screen.checkboxAction() == com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen.CheckboxAction.CLOSE
                    && store.isDismissed(uniqueId, screen.id())) {
                continue;
            }
            // ponytail: play the on-show sound via the audience (config phase
            // has no Player yet). Audience.playSound is supported on the
            // connection audience.
            if (screen.soundOnShow() != null && !screen.soundOnShow().isBlank()) {
                net.kyori.adventure.sound.Sound sound = settings.parseSound(screen.soundOnShow(), "screen_show");
                if (sound != null) {
                    audience.playSound(sound);
                }
            }
            audience.showDialog(dialogManager.createCustomScreenDialogForAudience(screen, i));
            return; // only the first matching screen, in config order
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        UUID uniqueId = event.getPlayerUniqueId();
        CompletableFuture<Boolean> future = pendingAuthentications.remove(uniqueId);
        if (future != null) {
            future.complete(false);
        }
        playerRegistrationStatus.remove(uniqueId);
        preAuthenticatedPlayers.remove(uniqueId);
        deferredPostJoinChecks.remove(uniqueId);
        connectionIps.remove(uniqueId);
        cancelledAuthentications.remove(uniqueId);
    }

    public void cancelAuthentication(UUID uniqueId) {
        cancelledAuthentications.add(uniqueId);
        completeAuthentication(uniqueId, false);
    }

    /**
     * Complete the authentication for a player in the configuration phase.
     *
     * @param uniqueId the player's UUID
     * @param success  whether authentication was successful
     */
    public void completeAuthentication(UUID uniqueId, boolean success) {
        CompletableFuture<Boolean> future = pendingAuthentications.get(uniqueId);
        if (future != null) {
            if (success) {
                preAuthenticatedPlayers.add(uniqueId);
            }
            future.complete(success);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        connectionIps.remove(uniqueId);

        if (preAuthenticatedPlayers.remove(uniqueId)) {
            authBridge.forceLogin(player);
            return;
        }

        if (deferredPostJoinChecks.remove(uniqueId)) {
            int delayTicks = settings.getConfigurationPhaseDeferredLoginCheckDelayTicks();
            player.getScheduler().runDelayed(plugin, scheduledTask -> {
                if (!player.isOnline() || player.hasPermission("authmeui.bypass")) {
                    return;
                }

                if (!authBridge.isPlayerAuthenticated(player)) {
                    // ponytail: use presentAuthDialog so PIN/slider modes are
                    // gated consistently for deferred post-join checks.
                    boolean isRegistered = authBridge.isPlayerRegistered(player.getName());
                    dialogManager.presentAuthDialog(player, isRegistered);
                }
            }, null, delayTicks);
        }
    }

    private boolean shouldDeferToPostJoin(UUID uniqueId, String playerName) {
        boolean sessionCompatible = settings.respectAuthMeSessionsInConfigurationPhase()
                && authBridge.canResumeSession(playerName, connectionIps.get(uniqueId));

        boolean fastLoginCompatible = settings.isFastLoginCompatibilityEnabled()
                && fastLoginDetected;

        return sessionCompatible || fastLoginCompatible;
    }

    /**
     * Check if a player is currently in the configuration phase authentication
     * process.
     *
     * @param uniqueId the player's UUID
     * @return true if the player is pending authentication
     */
    public boolean isPendingAuthentication(UUID uniqueId) {
        return pendingAuthentications.containsKey(uniqueId);
    }

    /**
     * Check if a player is registered (cached from configuration phase).
     *
     * @param uniqueId the player's UUID
     * @return true if registered, false if not, null if not in cache
     */
    public Boolean isPlayerRegistered(UUID uniqueId) {
        return playerRegistrationStatus.get(uniqueId);
    }

    /**
     * Update the registration status cache for a player.
     *
     * @param uniqueId     the player's UUID
     * @param isRegistered whether the player is registered
     */
    public void updateRegistrationStatus(UUID uniqueId, boolean isRegistered) {
        playerRegistrationStatus.put(uniqueId, isRegistered);
    }

    /**
     * Get the pending authentication futures map (for internal use).
     */
    public Map<UUID, CompletableFuture<Boolean>> getPendingAuthentications() {
        return pendingAuthentications;
    }

    /**
     * Get the connection IP map (for IpGuard hooks in the dialog interaction
     * listener, which needs the player's pre-login IP to track failed logins).
     */
    public Map<UUID, String> getConnectionIps() {
        return connectionIps;
    }
}
