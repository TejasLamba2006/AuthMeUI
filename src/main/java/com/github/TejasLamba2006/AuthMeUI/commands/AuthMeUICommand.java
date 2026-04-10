package com.github.TejasLamba2006.AuthMeUI.commands;

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

    private void sendUsageMessage(CommandSender sender, String label) {
        Component usageMsg = settings.getMessage("commands.usage",
                "<yellow>Usage: /%command% show <login|register> | reload</yellow>")
                .replaceText(builder -> builder.matchLiteral("%command%").replacement(label));
        sender.sendMessage(usageMsg);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("show");

            if (sender.hasPermission("authmeui.admin")) {
                suggestions.add("reload");
            }

            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            return filterSuggestions(DIALOG_TYPES, args[1]);
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
