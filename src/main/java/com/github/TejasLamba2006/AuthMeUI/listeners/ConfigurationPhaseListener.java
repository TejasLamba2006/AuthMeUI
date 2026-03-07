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

    public ConfigurationPhaseListener(
            AuthMeUIPlugin plugin,
            AuthenticationBridge authBridge,
            DialogManager dialogManager,
            SettingsManager settings) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
        this.settings = settings;
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

        // Create a completable future for this authentication attempt
        CompletableFuture<Boolean> authFuture = new CompletableFuture<>();
        int timeout = settings.getConfigurationPhaseTimeout();
        authFuture.completeOnTimeout(false, timeout, TimeUnit.SECONDS);

        pendingAuthentications.put(uniqueId, authFuture);

        Audience audience = connection.getAudience();

        // Show the appropriate dialog
        if (isRegistered) {
            audience.showDialog(dialogManager.createLoginDialogForAudience(playerName));
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
            connection
                    .disconnect(Component.text("Authentication timed out or failed. Please reconnect and try again."));
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
        if (preAuthenticatedPlayers.remove(player.getUniqueId())) {
            authBridge.forceLogin(player);
        }
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
}
