package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge.RegistrationSecondArgMode;
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

public class RegistrationDialogBuilder {

    private static final String PASSWORD_INPUT_KEY = "password";
    private static final String CONFIRM_INPUT_KEY = "confirm";
    private static final int MAX_PASSWORD_INPUT_LENGTH = 128;

    private final SettingsManager settings;
    private final AuthenticationBridge authBridge;

    public RegistrationDialogBuilder(SettingsManager settings, AuthenticationBridge authBridge) {
        this.settings = settings;
        this.authBridge = authBridge;
    }

    public Dialog construct(Player player) {
        return construct(player, null);
    }

    public Dialog construct(Player player, Component errorNotice) {
        List<DialogBody> contentSections = buildBodyContent();

        if (errorNotice != null) {
            contentSections.add(DialogBody.plainMessage(errorNotice));
        }

        List<TextDialogInput> inputFields = createInputFields();
        List<ActionButton> actionButtons = buildActionButtons();

        DialogBase dialogBase = DialogBase.builder(settings.getRegisterTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(inputFields)
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(actionButtons, null, settings.getButtonColumns()));
        });
    }

    private List<DialogBody> buildBodyContent() {
        List<DialogBody> content = new ArrayList<>();

        for (String line : settings.getRegisterBodyRaw()) {
            content.add(DialogBody.plainMessage(settings.parseText(line)));
        }

        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }

        return content;
    }

    private List<TextDialogInput> createInputFields() {
        TextDialogInput passwordField = DialogInput.text(PASSWORD_INPUT_KEY, settings.getRegisterPasswordLabel())
                .width(settings.getInputWidth())
                .labelVisible(true)
                .maxLength(MAX_PASSWORD_INPUT_LENGTH)
                .initial("")
                .build();

        RegistrationSecondArgMode secondArgMode = authBridge.getRegistrationSecondArgMode();

        if (secondArgMode == RegistrationSecondArgMode.NONE) {
            return List.of(passwordField);
        }

        TextDialogInput secondField = DialogInput.text(
                CONFIRM_INPUT_KEY,
                secondArgMode.usesEmail() ? settings.getRegisterEmailLabel() : settings.getRegisterConfirmLabel())
                .width(settings.getInputWidth())
                .labelVisible(true)
                .maxLength(MAX_PASSWORD_INPUT_LENGTH)
                .initial("")
                .build();

        return List.of(passwordField, secondField);
    }

    private List<ActionButton> buildActionButtons() {
        ActionButton submitButton = ActionButton.builder(settings.getRegisterSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.REGISTER_SUBMIT, null))
                .build();

        return settings.buildActionButtons("register-dialog", submitButton);
    }
}
