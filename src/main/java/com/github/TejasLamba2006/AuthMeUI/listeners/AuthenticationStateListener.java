package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.events.UnregisterByAdminEvent;
import fr.xephi.authme.events.UnregisterByPlayerEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AuthenticationStateListener implements Listener {

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;

    public AuthenticationStateListener(
            AuthMeUIPlugin plugin,
            AuthenticationBridge authBridge,
            DialogManager dialogManager) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogout(LogoutEvent event) {
        Player affectedPlayer = event.getPlayer();

        scheduleDialogPresentation(affectedPlayer, () -> {
            if (affectedPlayer.isOnline() && !authBridge.isPlayerAuthenticated(affectedPlayer)) {
                affectedPlayer.showDialog(dialogManager.createLoginDialog(affectedPlayer));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSelfUnregister(UnregisterByPlayerEvent event) {
        Player affectedPlayer = event.getPlayer();

        scheduleDialogPresentation(affectedPlayer, () -> {
            if (affectedPlayer != null && affectedPlayer.isOnline()) {
                dialogManager.presentAuthDialog(affectedPlayer, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdminUnregister(UnregisterByAdminEvent event) {
        String targetName = event.getPlayerName();
        Player affectedPlayer = Bukkit.getPlayerExact(targetName);

        if (affectedPlayer != null && affectedPlayer.isOnline()) {
            scheduleDialogPresentation(affectedPlayer, () -> dialogManager.presentAuthDialog(affectedPlayer, false));
        }
    }

    private void scheduleDialogPresentation(Player player, Runnable action) {
        if (player == null) {
            return;
        }

        player.getScheduler().run(plugin, scheduledTask -> action.run(), null);
    }
}
