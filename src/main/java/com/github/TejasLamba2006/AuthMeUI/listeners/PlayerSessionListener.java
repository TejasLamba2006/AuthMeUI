package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
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
    // ponytail: re-present interval for the escape guard. When escape-close is
    // disabled, a player who still closes the dialog (misclick, custom click)
    // would otherwise be stranded unauthenticated. Re-show every 100 ticks (5s)
    // until they authenticate or leave. Tunable if a visible flicker matters.
    private static final int REOPEN_INTERVAL_TICKS = 100;

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;
    private final SettingsManager settings;

    public PlayerSessionListener(AuthMeUIPlugin plugin, AuthenticationBridge authBridge,
            DialogManager dialogManager, SettingsManager settings) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
        this.settings = settings;
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
        final int[] ticksSinceLastShow = { 0 };
        final boolean[] dialogShown = { false };

        player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                return;
            }

            // ponytail: O — bedrock (Floodgate/Geyser) auto-login. Before
            // showing any dialog, if bedrock auto-login is enabled and this is
            // a Floodgate player, force them in (mirrors AuthMeBia's
            // resolveAutoLoginJavaUuid + completeAutoLogin, minus its
            // placeholder-password DataSource reflection — AuthMe handles
            // bedrock registration itself when Floodgate is installed, so we
            // only forceLogin registered bedrock players and let the normal
            // register dialog handle unregistered ones). GEYSER mode trusts
            // the bedrock uuid verbatim; LINK mode requires an actual
            // Floodgate account link (getLinkedJavaUuid resolves), which is
            // the safe default.
            if (!authBridge.isPlayerAuthenticated(player)
                    && settings.isBedrockAutoLoginEnabled()
                    && com.github.TejasLamba2006.AuthMeUI.authentication.Floodgate
                            .isFloodgatePlayer(player.getUniqueId())
                    && authBridge.isPlayerRegistered(player.getName())) {
                boolean allowed = settings.getBedrockAutoLoginMode()
                        == SettingsManager.BedrockAutoLoginMode.GEYSER
                        || com.github.TejasLamba2006.AuthMeUI.authentication.Floodgate
                                .getLinkedJavaUuid(player.getUniqueId()) != null;
                if (allowed) {
                    authBridge.forceLogin(player);
                    scheduledTask.cancel();
                    return;
                }
            }

            if (authBridge.isPlayerAuthenticated(player)) {
                // ponytail: K — premium password-set. A premium-auto-login
                // player (FastLogin/mojang) is already authenticated, so the
                // normal watchdog cancels here. But they may have no real
                // AuthMe password set. Prompt once per session to set one,
                // so a later premium-service outage doesn't lock them out.
                // AuthMeBia re-prompts every login via a placeholder-pw
                // mechanism; AuthMeUI has no placeholder, so once per session.
                if (settings.isPremiumSetEnabled()
                        && authBridge.isPremiumSkip(player.getUniqueId(), player.getName())
                        && dialogManager.getPremiumSetTracker().shouldShow(player.getUniqueId())) {
                    dialogManager.getPremiumSetTracker().markShown(player.getUniqueId());
                    dialogManager.markRecoverMode(player.getUniqueId(),
                            DialogManager.RecoverMode.PREMIUM_SET);
                    player.showDialog(dialogManager.createPremiumSetDialog(player, false));
                }
                scheduledTask.cancel();
                return;
            }

            elapsedTicks[0] += CHECK_INTERVAL_TICKS;

            // Wait for AuthMe to finish its own join-time auth checks before we
            // present a dialog, so we don't fight AuthMe's session resume.
            if (!dialogShown[0] && elapsedTicks[0] < MAX_WAIT_TICKS) {
                return;
            }

            if (player.getProtocolVersion() < MIN_DIALOG_CLIENT_PROTOCOL) {
                return;
            }

            boolean hasAccount = authBridge.isPlayerRegistered(player.getName());

            // First presentation.
            if (!dialogShown[0]) {
                dialogManager.presentAuthDialog(player, hasAccount);
                dialogShown[0] = true;
                ticksSinceLastShow[0] = 0;
                return;
            }

            // Escape guard: if the player is still unauthenticated after the
            // initial dialog, they closed it. Re-present on the reopen interval
            // unless the server allows escape-close (then leave them be).
            ticksSinceLastShow[0] += CHECK_INTERVAL_TICKS;
            // ponytail: skip for Floodgate players — re-presenting restarts the flow
            // (register -> rules page) and wipes typed input every 5s. Ceiling: a
            // Bedrock player who closes the form must rejoin or use /authmeui show.
            if (ticksSinceLastShow[0] >= REOPEN_INTERVAL_TICKS
                    && !plugin.getSettingsManager().canCloseWithEscape()
                    && !com.github.TejasLamba2006.AuthMeUI.authentication.Floodgate
                            .isFloodgatePlayer(player.getUniqueId())) {
                dialogManager.presentAuthDialog(player, hasAccount);
                ticksSinceLastShow[0] = 0;
            }
        }, null, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }
}
