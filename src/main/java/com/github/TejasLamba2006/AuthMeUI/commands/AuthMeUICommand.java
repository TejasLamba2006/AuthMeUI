package com.github.TejasLamba2006.AuthMeUI.commands;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuthMeUICommand implements TabExecutor {

    private static final List<String> DIALOG_TYPES = List.of("login", "register");

    private final SettingsManager settings;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;

    public AuthMeUICommand(SettingsManager settings, AuthenticationBridge authBridge, DialogManager dialogManager) {
        this.settings = settings;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendUsageMessage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        return switch (subCommand) {
            case "reload" -> executeReload(sender);
            case "show" -> executeShowDialog(sender, args, label);
            case "screen" -> executeScreen(sender, args, label);
            case "reset" -> executeReset(sender, args, label);
            default -> {
                sendUsageMessage(sender, label);
                yield true;
            }
        };
    }

    private boolean executeReload(CommandSender sender) {
        if (!sender.hasPermission("authmeui.admin")) {
            Component noPermMsg = settings.getMessage("commands.no-permission",
                    "<red>You don't have permission to do that!</red>");
            sender.sendMessage(noPermMsg);
            return true;
        }

        settings.reload();
        authBridge.refreshAuthMeSettingsCache();

        Component successMsg = settings.getMessage("commands.config-reloaded",
                "<green>AuthMeUI configuration reloaded successfully!</green>");
        sender.sendMessage(successMsg);
        return true;
    }

    private boolean executeShowDialog(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            Component playerOnlyMsg = settings.getMessage("commands.player-only",
                    "<red>This command can only be used by players!</red>");
            sender.sendMessage(playerOnlyMsg);
            return true;
        }

        if (!authBridge.isConnected()) {
            Component unavailableMsg = settings.getMessage("commands.authme-unavailable",
                    "<red>AuthMe is not available!</red>");
            player.sendMessage(unavailableMsg);
            return true;
        }

        if (args.length < 2) {
            sendUsageMessage(sender, label);
            return true;
        }

        String dialogType = args[1].toLowerCase(Locale.ROOT);

        switch (dialogType) {
            case "login" -> {
                player.showDialog(dialogManager.createLoginDialog(player));
                Component openedMsg = settings.getMessage("commands.dialog-opened", "<gray>Dialog opened.</gray>");
                player.sendMessage(openedMsg);
            }
            case "register" -> {
                player.showDialog(dialogManager.createRegistrationDialog(player));
                Component openedMsg = settings.getMessage("commands.dialog-opened", "<gray>Dialog opened.</gray>");
                player.sendMessage(openedMsg);
            }
            default -> {
                Component invalidMsg = settings.getMessage("commands.invalid-dialog",
                        "<yellow>Invalid dialog type. Use: login or register</yellow>");
                player.sendMessage(invalidMsg);
            }
        }

        return true;
    }

    /**
     * /authmeui screen &lt;id&gt; — show a custom screen to yourself (Player only).
     * /authmeui screen reset &lt;player&gt; [id] — clear dismiss-forever records
     * for a player (works offline). Admin permission required for reset.
     *
     * <p>ponytail: mirrors AuthMeBia's {@code /bia screen} subcommand but
     * self-only for the show path (matching the existing {@code show}
     * command's self-only contract). Manual show bypasses the dismiss
     * check so admins can re-display a previously-dismissed screen.
     */
    private boolean executeScreen(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsageMessage(sender, label);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("reset")) {
            // /authmeui screen reset <player> [id]
            if (!sender.hasPermission("authmeui.admin")) {
                sender.sendMessage(settings.getMessage("commands.no-permission",
                        "<red>You don't have permission to do that!</red>"));
                return true;
            }
            if (args.length < 3) {
                sendUsageMessage(sender, label);
                return true;
            }
            String targetName = args[2];
            org.bukkit.OfflinePlayer target = sender.getServer().getOfflinePlayer(targetName);
            java.util.UUID uuid = target.getUniqueId();
            if (uuid == null) {
                sender.sendMessage(settings.getMessage("commands.screen-reset-failed",
                        "<red>Could not resolve a player UUID for '" + targetName + "'.</red>"));
                return true;
            }
            com.github.TejasLamba2006.AuthMeUI.dialogs.ScreenDismissStore store =
                    dialogManager.getScreenDismissStore();
            if (store == null) {
                sender.sendMessage(settings.getMessage("commands.screen-reset-unavailable",
                        "<red>Dismiss store is not initialized.</red>"));
                return true;
            }
            if (args.length >= 4) {
                String id = args[3];
                store.clear(uuid, id);
                sender.sendMessage(settings.getMessage("commands.screen-reset-cleared",
                        "<green>Cleared dismiss record for screen '" + id + "' for " + targetName + ".</green>"));
            } else {
                store.clearAll(uuid);
                sender.sendMessage(settings.getMessage("commands.screen-reset-cleared-all",
                        "<green>Cleared all screen dismiss records for " + targetName + ".</green>"));
            }
            return true;
        }

        // /authmeui screen <id> — show to self (Player only)
        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.getMessage("commands.player-only",
                    "<red>This command can only be used by players!</red>"));
            return true;
        }

        String id = sub;
        com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen screen = settings.getCustomScreen(id);
        if (screen == null || !screen.enabled()) {
            sender.sendMessage(settings.getMessage("commands.screen-not-found",
                    "<red>No enabled custom screen with id '" + id + "' found in config.yml.</red>"));
            return true;
        }

        // ponytail: manual show bypasses the dismiss check (matching
        // AuthMeBia) so admins can re-display a dismissed screen.
        int idx = settings.getCustomScreenIndex(id);
        if (idx < 0) {
            idx = 0; // defensive: parse produced a screen but index lookup failed
        }
        player.showDialog(dialogManager.createCustomScreenDialog(player, screen, idx));
        sender.sendMessage(settings.getMessage("commands.dialog-opened", "<gray>Dialog opened.</gray>"));
        return true;
    }

    private void sendUsageMessage(CommandSender sender, String label) {
        Component usageMsg = settings.getMessage("commands.usage",
                "<yellow>Usage: /%command% show <login|register> | screen <id> | screen reset <player> [id] | reset <player> | reload</yellow>")
                .replaceText(builder -> builder.matchLiteral("%command%").replacement(label));
        sender.sendMessage(usageMsg);
    }

    /**
     * /authmeui reset &lt;player&gt; — flag a player for a forced password reset
     * on next authentication (feature L). Mirrors AuthMeBia's
     * {@code /bia recover <player>}. Admin permission required.
     *
     * <p>ponytail: if the player is online, the recover dialog is shown
     * immediately via {@code presentAuthDialog}; if offline, the flag persists
     * (flat-file) and the login path intercepts it on next join.
     */
    private boolean executeReset(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("authmeui.admin")) {
            sender.sendMessage(settings.getMessage("commands.no-permission",
                    "<red>You don't have permission to do that!</red>"));
            return true;
        }
        if (!authBridge.isConnected()) {
            sender.sendMessage(settings.getMessage("commands.authme-unavailable",
                    "<red>AuthMe is not available!</red>"));
            return true;
        }
        if (args.length < 2) {
            sendUsageMessage(sender, label);
            return true;
        }

        String targetName = args[1];
        if (!authBridge.isPlayerRegistered(targetName)) {
            sender.sendMessage(settings.getMessage("commands.reset-not-registered",
                    "<red>" + targetName + " is not registered.</red>"));
            return true;
        }

        com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverStore store =
                dialogManager.getRecoverStore();
        if (store == null) {
            sender.sendMessage(settings.getMessage("commands.reset-unavailable",
                    "<red>Recover store is not initialized.</red>"));
            return true;
        }

        org.bukkit.OfflinePlayer target = sender.getServer().getOfflinePlayer(targetName);
        java.util.UUID uuid = target.getUniqueId();
        if (uuid == null) {
            sender.sendMessage(settings.getMessage("commands.reset-failed",
                    "<red>Could not resolve a UUID for '" + targetName + "'.</red>"));
            return true;
        }

        store.flag(uuid, targetName);
        sender.sendMessage(settings.getMessage("commands.reset-flagged",
                "<green>Flagged " + targetName + " for a forced password reset on next authentication.</green>"));

        // ponytail: N — notify the Discord webhook that an admin forced a
        // reset, so resets are auditable off-server. Async, fire-and-forget.
        if (settings.isDiscordWebhookEnabled() && settings.isDiscordNotifyAdminReset()) {
            var webhook = dialogManager.getDiscordWebhook();
            if (webhook != null) {
                String url = settings.getDiscordWebhookUrl();
                String admin = sender.getName();
                AuthMeUIPlugin plugin = AuthMeUIPlugin.getInstance();
                plugin.getServer().getAsyncScheduler().runNow(plugin,
                        asyncTask1 -> webhook.sendEmbed(url,
                                "Admin-Forced Password Reset",
                                admin + " flagged " + targetName + " for a forced password reset.",
                                settings.getDiscordAdminResetColor()));
            }
        }

        // ponytail: if the target is online and not yet authenticated, show
        // the recover dialog immediately (mirrors AuthMeBia's online path).
        Player online = sender.getServer().getPlayer(uuid);
        if (online != null && online.isOnline() && !authBridge.isPlayerAuthenticated(online)) {
            dialogManager.markRecoverMode(uuid, DialogManager.RecoverMode.ADMIN_RESET);
            online.showDialog(dialogManager.createRecoverDialog(online, false));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("show");
            suggestions.add("screen");

            if (sender.hasPermission("authmeui.admin")) {
                suggestions.add("reload");
                suggestions.add("reset");
            }

            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            return filterSuggestions(DIALOG_TYPES, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("screen")) {
            // /authmeui screen <id|reset>
            List<String> suggestions = new ArrayList<>(settings.getCustomScreenIds());
            if (sender.hasPermission("authmeui.admin")) {
                suggestions.add("reset");
            }
            return filterSuggestions(suggestions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("screen") && args[1].equalsIgnoreCase("reset")) {
            // /authmeui screen reset <player> — tab-complete online player names
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player p : sender.getServer().getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filterSuggestions(names, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("screen") && args[1].equalsIgnoreCase("reset")) {
            // /authmeui screen reset <player> [id]
            return filterSuggestions(settings.getCustomScreenIds(), args[3]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            // /authmeui reset <player> — tab-complete online player names
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player p : sender.getServer().getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filterSuggestions(names, args[1]);
        }

        return List.of();
    }

    private List<String> filterSuggestions(List<String> options, String input) {
        String lowercaseInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
        List<String> filtered = new ArrayList<>();

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowercaseInput)) {
                filtered.add(option);
            }
        }

        return filtered;
    }
}
