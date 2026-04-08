package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LoginDialogBuilder {

    private static final String PASSWORD_INPUT_KEY = "password";
    private static final int MAX_PASSWORD_INPUT_LENGTH = 128;

    private final SettingsManager settings;

    public LoginDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player) {
        return construct(player, null);
    }

    public Dialog construct(Player player, Component errorNotice) {
        List<DialogBody> contentSections = buildBodyContent();

        if (errorNotice != null) {
            contentSections.add(DialogBody.plainMessage(errorNotice));
        }

        TextDialogInput passwordField = createPasswordInput();
        List<ActionButton> actionButtons = buildActionButtons();

        DialogBase dialogBase = DialogBase.builder(settings.getLoginTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(passwordField))
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(actionButtons, null, settings.getButtonColumns()));
        });
    }

    private List<DialogBody> buildBodyContent() {
        List<DialogBody> content = new ArrayList<>();

        for (String line : settings.getLoginBodyRaw()) {
            content.add(DialogBody.plainMessage(settings.parseText(line)));
        }

        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }

        return content;
    }

    private TextDialogInput createPasswordInput() {
        return DialogInput.text(PASSWORD_INPUT_KEY, settings.getLoginPasswordLabel())
                .width(settings.getInputWidth())
                .labelVisible(true)
                .maxLength(MAX_PASSWORD_INPUT_LENGTH)
                .initial("")
                .build();
    }

    private List<ActionButton> buildActionButtons() {
        ActionButton submitButton = ActionButton.builder(settings.getLoginSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.LOGIN_SUBMIT, null))
                .build();

        ActionButton cancelButton = ActionButton.builder(settings.getLoginCancelButton())
                .action(DialogAction.customClick(DialogIdentifiers.LOGIN_CANCEL, null))
                .build();

        return settings.buildLoginActionButtons(submitButton, cancelButton);
    }
}
