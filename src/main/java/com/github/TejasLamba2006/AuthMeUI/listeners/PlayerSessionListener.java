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

        scheduleAuthenticationCheck(joiningPlayer);
    }

    private void scheduleAuthenticationCheck(Player player) {
        final int[] elapsedTicks = { 0 };

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

            if (elapsedTicks[0] >= MAX_WAIT_TICKS) {
                boolean hasAccount = authBridge.isPlayerRegistered(player.getName());
                dialogManager.presentAuthDialog(player, hasAccount);
                scheduledTask.cancel();
            }
        }, null, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }
}
