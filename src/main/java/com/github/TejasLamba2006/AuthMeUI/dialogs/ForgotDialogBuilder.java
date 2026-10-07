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
 * Builds the forgot-password email-entry dialog (stage 1 of the AuthMeBia
 * forgot flow). Single text input ("email"); submit sends a verification
 * code, back returns to login. A no-email variant is shown when the player
 * has no email linked to their account.
 *
 * <p>AuthMeBia blocks on a CountDownLatch and recurses; AuthMeUI is
 * event-driven, so session state (email/code/lastSent/wrongTries) lives on
 * the listener keyed by UUID between clicks.
 */
public class ForgotDialogBuilder {

    public static final String EMAIL_INPUT_KEY = "email";
    private static final int MAX_EMAIL_INPUT_LENGTH = 254;

    private final SettingsManager settings;

    public ForgotDialogBuilder(SettingsManager settings) {
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

        TextDialogInput emailField = createEmailInput(errorNotice, player);

        ActionButton submitButton = ActionButton.builder(settings.getForgotEmailSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_SUBMIT, null))
                .build();

        ActionButton backButton = ActionButton.builder(settings.getForgotBackButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_BACK, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getForgotEmailTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(emailField))
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(submitButton, backButton), null, 1));
        });
    }

    /**
     * Build the no-email dialog: informs the player no email is linked and
     * offers a back button (returns to login). No inputs.
     */
    public Dialog constructNoEmail(Player player) {
        return buildNoEmail(player);
    }

    public Dialog constructNoEmailForAudience() {
        return buildNoEmail(null);
    }

    private Dialog buildNoEmail(Player player) {
        List<DialogBody> contentSections = new ArrayList<>();
        for (String line : settings.getForgotNoEmailBodyRaw()) {
            contentSections.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }
        if (contentSections.isEmpty()) {
            contentSections.add(DialogBody.plainMessage(Component.empty()));
        }

        ActionButton backButton = ActionButton.builder(settings.getForgotBackButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_BACK, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getForgotNoEmailTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(backButton), null, 1));
        });
    }

    private List<DialogBody> buildBodyContent(Player player) {
        List<DialogBody> content = new ArrayList<>();
        for (String line : settings.getForgotEmailBodyRaw()) {
            content.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }
        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }
        return content;
    }

    private TextDialogInput createEmailInput(Component errorNotice, Player player) {
        String baseLabel = settings.getForgotEmailLabel();
        String label = errorNotice != null
                ? baseLabel + "  [" + plainText(errorNotice) + "]"
                : baseLabel;

        return DialogInput.text(EMAIL_INPUT_KEY, settings.parseText(label, player))
                .width(settings.getInputWidth(player))
                .labelVisible(true)
                .maxLength(MAX_EMAIL_INPUT_LENGTH)
                .initial("")
                .build();
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static String plainText(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }
}
