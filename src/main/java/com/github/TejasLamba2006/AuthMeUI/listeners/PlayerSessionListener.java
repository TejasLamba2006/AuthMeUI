package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles post-join authentication dialogs.
 * This listener is only registered when configuration phase authentication is
 * DISABLED.
 */
public class PlayerSessionListener implements Listener {

    private static final int INITIAL_DELAY_TICKS = 5;
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final int MAX_WAIT_TICKS = 20;
    private static final int MIN_DIALOG_CLIENT_PROTOCOL = 769; // Minecraft 1.21.6

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;

    public PlayerSessionListener(AuthMeUIPlugin plugin, AuthenticationBridge authBridge, DialogManager dialogManager) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        if (!authBridge.isConnected()) {
            return;
        }

        if (joiningPlayer.hasPermission("authmeui.bypass")) {
            return;
        }

        scheduleAuthenticationWatchdog(joiningPlayer);
    }

    private void scheduleAuthenticationWatchdog(Player player) {
        final int[] elapsedTicks = { 0 };
        final boolean[] dialogShown = { false };

        player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                return;
            }

            if (authBridge.isPlayerAuthenticated(player)) {
                scheduledTask.cancel();
                return;
            }

            elapsedTicks[0] += CHECK_INTERVAL_TICKS;

            if (dialogShown[0] || elapsedTicks[0] < MAX_WAIT_TICKS) {
                return;
            }

            if (player.getProtocolVersion() >= MIN_DIALOG_CLIENT_PROTOCOL) {
                boolean hasAccount = authBridge.isPlayerRegistered(player.getName());
                dialogManager.presentAuthDialog(player, hasAccount);
            }

            dialogShown[0] = true;
        }, null, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }
}
