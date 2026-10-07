package com.github.TejasLamba2006.AuthMeUI.dialogs;

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

/**
 * Builds the password-recovery dialog (stage 3 of the forgot flow). Two
 * text inputs ("new_password" + "confirm_password"); on submit the listener
 * validates they match, then calls {@code authBridge.changePassword} async
 * and returns the player to login on success.
 *
 * <p>Mirrors AuthMeBia's {@code Recover.java}. A mismatch is surfaced by
 * appending {@code "  [mismatch]"} to the confirm-field label.
 */
public class RecoverDialogBuilder {

    public static final String NEW_PASSWORD_KEY = "new_password";
    public static final String CONFIRM_PASSWORD_KEY = "confirm_password";
    private static final int MAX_PASSWORD_INPUT_LENGTH = 64;

    private final SettingsManager settings;

    public RecoverDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player, boolean mismatch) {
        return build(mismatch, player);
    }

    public Dialog constructForAudience(boolean mismatch) {
        return build(mismatch, null);
    }

    private Dialog build(boolean mismatch, Player player) {
        List<DialogBody> contentSections = buildBodyContent(player);

        TextDialogInput newPassField = createPasswordInput(
                NEW_PASSWORD_KEY, settings.getRecoverNewPasswordLabel(), false, player);
        TextDialogInput confirmField = createPasswordInput(
                CONFIRM_PASSWORD_KEY, settings.getRecoverConfirmPasswordLabel(), mismatch, player);

        ActionButton submitButton = ActionButton.builder(settings.getRecoverSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.RECOVER_SUBMIT, null))
                .build();

        ActionButton cancelButton = ActionButton.builder(settings.getForgotBackButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_BACK, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getRecoverTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(newPassField, confirmField))
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(submitButton, cancelButton), null, 1));
        });
    }

    private List<DialogBody> buildBodyContent(Player player) {
        List<DialogBody> content = new ArrayList<>();
        for (String line : settings.getRecoverBodyRaw()) {
            content.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }
        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }
        return content;
    }

    private TextDialogInput createPasswordInput(
            String key, String baseLabel, boolean mismatch, Player player) {
        String label = mismatch ? baseLabel + "  [mismatch]" : baseLabel;
        return DialogInput.text(key, settings.parseText(label, player))
                .width(settings.getInputWidth(player))
                .labelVisible(true)
                .maxLength(MAX_PASSWORD_INPUT_LENGTH)
                .initial("")
                .build();
    }
}
