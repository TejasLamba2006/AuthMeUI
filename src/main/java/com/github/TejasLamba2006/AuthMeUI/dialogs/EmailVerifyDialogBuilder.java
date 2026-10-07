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
 * Builds the email-verification dialog (stage 2 of the forgot flow). The
 * player types the numeric code AuthMe emailed them. Body lines may use
 * {email} and {cooldown} placeholders (cooldown = resend cooldown remaining).
 * The resend button shows a cooldown-styled label while on cooldown.
 *
 * <p>Wrong-code tries and the resend cooldown are tracked by the listener
 * (per-UUID session), mirroring AuthMeBia's ForgotPasswordSession.
 */
public class EmailVerifyDialogBuilder {

    public static final String CODE_INPUT_KEY = "code";
    private static final int MAX_CODE_INPUT_LENGTH = 16;

    private final SettingsManager settings;

    public EmailVerifyDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player, String email, long cooldownRemaining, Component errorNotice) {
        return build(email, cooldownRemaining, errorNotice, player);
    }

    public Dialog constructForAudience(String email, long cooldownRemaining, Component errorNotice) {
        return build(email, cooldownRemaining, errorNotice, null);
    }

    private Dialog build(String email, long cooldownRemaining, Component errorNotice, Player player) {
        List<DialogBody> contentSections = buildBodyContent(email, cooldownRemaining, player);

        TextDialogInput codeField = createCodeInput(errorNotice, player);

        ActionButton verifyButton = ActionButton.builder(settings.getEmailVerifyButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_VERIFY, null))
                .build();

        // ponytail: resend label swaps to a cooldown variant while on cooldown.
        // The action key stays FORGOT_RESEND; the handler re-checks cooldown and
        // re-shows with an updated label if the player clicks early.
        Component resendLabel = cooldownRemaining > 0
                ? settings.getEmailResendCooldownLabel(cooldownRemaining)
                : settings.getEmailResendButton();
        ActionButton resendButton = ActionButton.builder(resendLabel)
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_RESEND, null))
                .build();

        ActionButton cancelButton = ActionButton.builder(settings.getForgotBackButton())
                .action(DialogAction.customClick(DialogIdentifiers.FORGOT_BACK, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getEmailVerifyTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(codeField))
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(verifyButton, resendButton, cancelButton), null, 1));
        });
    }

    private List<DialogBody> buildBodyContent(String email, long cooldownRemaining, Player player) {
        List<DialogBody> content = new ArrayList<>();
        String escapedEmail = escapeEmail(email);
        String cooldownText = String.valueOf(Math.max(0, cooldownRemaining));

        for (String line : settings.getEmailVerifyBodyRaw()) {
            String resolved = line.replace("{email}", escapedEmail)
                    .replace("{cooldown}", cooldownText);
            content.add(DialogBody.plainMessage(settings.parseText(resolved, player)));
        }
        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }
        return content;
    }

    /**
     * Escape a stored email for embedding in a MiniMessage string. The email
     * is attacker-influenced (sourced from AuthMe's DB) and is being spliced
     * into a raw string that MiniMessage will parse, so {@code <}/{@code >}
     * must be escaped to prevent tag injection.
     */
    private static String escapeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.replace("<", "\\<").replace(">", "\\>");
    }

    private TextDialogInput createCodeInput(Component errorNotice, Player player) {
        String baseLabel = settings.getEmailCodeLabel();
        String label = errorNotice != null
                ? baseLabel + "  [" + plainText(errorNotice) + "]"
                : baseLabel;

        return DialogInput.text(CODE_INPUT_KEY, settings.parseText(label, player))
                .width(settings.getInputWidth(player))
                .labelVisible(true)
                .maxLength(MAX_CODE_INPUT_LENGTH)
                .initial("")
                .build();
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static String plainText(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }
}
