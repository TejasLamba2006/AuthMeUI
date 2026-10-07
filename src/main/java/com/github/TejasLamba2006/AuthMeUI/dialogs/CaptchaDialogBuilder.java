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
 * Builds the captcha verification dialog. The code to type is rendered in the
 * body (via a {@code %code%} placeholder in the configured body lines), and
 * an error notice — if any — is appended to the input label the way AuthMeBia
 * does it ({@code "Label  [error]"}), since a dialog input has no separate
 * error field.
 */
public class CaptchaDialogBuilder {

    public static final String CODE_INPUT_KEY = "code";
    private static final int MAX_CODE_INPUT_LENGTH = 16;

    private final SettingsManager settings;

    public CaptchaDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player, String code, Component errorNotice) {
        return build(code, errorNotice, player);
    }

    public Dialog constructForAudience(String code, Component errorNotice) {
        return build(code, errorNotice, null);
    }

    private Dialog build(String code, Component errorNotice, Player player) {
        List<DialogBody> contentSections = buildBodyContent(code, player);

        TextDialogInput codeField = createCodeInput(errorNotice, player);

        ActionButton submitButton = ActionButton.builder(settings.getCaptchaSubmitButton())
                .action(DialogAction.customClick(DialogIdentifiers.CAPTCHA_SUBMIT, null))
                .build();

        DialogBase dialogBase = DialogBase.builder(settings.getCaptchaTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(contentSections)
                .inputs(List.of(codeField))
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(List.of(submitButton), null, 1));
        });
    }

    private List<DialogBody> buildBodyContent(String code, Player player) {
        List<DialogBody> content = new ArrayList<>();

        for (String line : settings.getCaptchaBodyRaw()) {
            String resolved = line.replace("%code%", code != null ? code : "");
            content.add(DialogBody.plainMessage(settings.parseText(resolved, player)));
        }

        if (content.isEmpty()) {
            content.add(DialogBody.plainMessage(Component.empty()));
        }

        return content;
    }

    private TextDialogInput createCodeInput(Component errorNotice, Player player) {
        String baseLabel = settings.getCaptchaInputLabel();
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
