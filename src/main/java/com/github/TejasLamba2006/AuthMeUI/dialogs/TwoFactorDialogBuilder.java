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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the TOTP two-factor-authentication dialog. Mirrors AuthMeBia's
 * {@code dialog/twofactor/TwoFactor.java}: single text input ("totp_code"),
 * submit + cancel buttons, error embedded in the input label as
 * {@code "Label  [error]"} (a dialog input has no separate error field).
 *
 * <p>AuthMeBia uses a blocking loop with a CountDownLatch; AuthMeUI is
 * event-driven, so wrong-code tries are tracked by the listener (per player)
 * and the dialog is re-shown on the next tick.
 */
public class TwoFactorDialogBuilder {

    public static final String TOTP_INPUT_KEY = "totp_code";
    private static final int MAX_TOTP_INPUT_LENGTH = 16;

    private final SettingsManager settings;

    public TwoFactorDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player, Component errorNotice) {
        return build(errorNotice, player);
    }

    public Dialog constructForAudience(Component errorNotice) {
        return build(errorNotice, null);
    }

    private Dialog build(Component errorNotice, Player player) {
        List<DialogBody> contentSections = buildBodyContent(player);

        TextDialogInput codeField = createCodeInput(errorNotice, player);

        ActionButton submitButton = ActionButton.builder(settings.getTotpSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.TOTP_SUBMIT, null))
                .build();

        ActionButton cancelButton = ActionButton.builder(settings.getTotpCancelButton())
                .action(DialogAction.customClick(DialogIdentifiers.TOTP_CANCEL, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getTotpTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(codeField))
                .build();

        // ponytail: 1 column matches AuthMeBia's single-button rows; the cancel
        // button sits below submit. Use multiAction with 1 column.
        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(submitButton, cancelButton), null, 1));
        });
    }

    private List<DialogBody> buildBodyContent(Player player) {
        List<DialogBody> content = new ArrayList<>();

        for (String line : settings.getTotpBodyRaw()) {
            content.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }

        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }

        return content;
    }

    private TextDialogInput createCodeInput(Component errorNotice, Player player) {
        String baseLabel = settings.getTotpInputLabel();
        String label = errorNotice != null
                ? baseLabel + "  [" + plainText(errorNotice) + "]"
                : baseLabel;

        return DialogInput.text(TOTP_INPUT_KEY, settings.parseText(label, player))
                .width(settings.getInputWidth(player))
                .labelVisible(true)
                .maxLength(MAX_TOTP_INPUT_LENGTH)
                .initial("")
                .build();
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static String plainText(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }
}
