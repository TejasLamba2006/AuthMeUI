package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
        new AuthenticationCheckTask(player).runTaskTimer(plugin, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }

    private class AuthenticationCheckTask extends BukkitRunnable {

        private final Player targetPlayer;
        private int elapsedTicks = 0;

        AuthenticationCheckTask(Player player) {
            this.targetPlayer = player;
        }

        @Override
        public void run() {
            if (!targetPlayer.isOnline()) {
                cancel();
                return;
            }

            if (authBridge.isPlayerAuthenticated(targetPlayer)) {
                cancel();
                return;
            }

            elapsedTicks += CHECK_INTERVAL_TICKS;

            if (elapsedTicks >= MAX_WAIT_TICKS) {
                presentAppropriateDialog();
                cancel();
            }
        }

        private void presentAppropriateDialog() {
            boolean hasAccount = authBridge.isPlayerRegistered(targetPlayer.getName());
            dialogManager.presentAuthDialog(targetPlayer, hasAccount);
        }
    }
}
