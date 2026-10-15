package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.events.RegisterEvent;
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
    private final SettingsManager settings;

    public AuthenticationStateListener(
            AuthMeUIPlugin plugin,
            AuthenticationBridge authBridge,
            DialogManager dialogManager) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogout(LogoutEvent event) {
        Player affectedPlayer = event.getPlayer();

        if (settings.isDiscordWebhookEnabled() && settings.isDiscordNotifyLogout()) {
            notifyDiscord("Player Logged Out", affectedPlayer.getName() + " logged out.",
                    settings.getDiscordLogoutColor());
        }

        scheduleDialogPresentation(affectedPlayer, () -> {
            if (affectedPlayer.isOnline() && !authBridge.isPlayerAuthenticated(affectedPlayer)) {
                affectedPlayer.showDialog(dialogManager.createLoginDialog(affectedPlayer));
            }
        });
    }

    /**
     * On successful AuthMe login, show the first POSTJOIN-trigger custom
     * screen (feature J) that the player hasn't permanently dismissed.
     * Mirrors AuthMeBia's {@code showPostJoinScreens}, which iterates in
     * config order and stops after the first shown screen.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthMeLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (settings.isDiscordWebhookEnabled() && settings.isDiscordNotifyLogin()) {
            notifyDiscord("Player Logged In", player.getName() + " authenticated successfully.",
                    settings.getDiscordLoginColor());
        }
        scheduleDialogPresentation(player, () -> showPostJoinScreens(player));
    }

    /**
     * On successful AuthMe registration, also show POSTJOIN screens (a
     * player who just registered is authenticated and should see the same
     * post-auth screens).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthMeRegister(RegisterEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (settings.isDiscordWebhookEnabled() && settings.isDiscordNotifyRegister()) {
            notifyDiscord("Player Registered", player.getName() + " registered a new account.",
                    settings.getDiscordRegisterColor());
        }
        scheduleDialogPresentation(player, () -> showPostJoinScreens(player));
    }

    /**
     * Show the first enabled, non-dismissed, POSTJOIN-trigger custom screen.
     */
    private void showPostJoinScreens(Player player) {
        if (!player.isOnline()) return;
        java.util.List<CustomScreen> screens = settings.getCustomScreens();
        for (int i = 0; i < screens.size(); i++) {
            CustomScreen screen = screens.get(i);
            if (!screen.enabled()) continue;
            if (screen.trigger() != CustomScreen.Trigger.POSTJOIN) continue;
            dialogManager.showCustomScreen(player, screen, i);
            return; // ponytail: only the first matching screen, in config order
        }
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

    /**
     * Ponytail: fire the webhook embed on an async thread — HttpClient send
     * blocks, so it must not run on the main thread. No-op if webhook
     * disabled or unbound.
     */
    private void notifyDiscord(String title, String description, int color) {
        var webhook = dialogManager.getDiscordWebhook();
        if (webhook == null) return;
        String url = settings.getDiscordWebhookUrl();
        if (url == null || url.isBlank()) return;
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                asyncTask1 -> webhook.sendEmbed(url, title, description, color));
    }
}
