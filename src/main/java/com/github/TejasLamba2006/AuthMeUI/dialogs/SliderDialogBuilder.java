package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the slider login dialog (mode H). For a length-L code, renders L
 * ▲ buttons, L value labels (current digit per slot), L ▼ buttons, and a
 * confirm. Up/down buttons carry {@link DialogIdentifiers#SLIDER_UP} /
 * {@link DialogIdentifiers#SLIDER_DOWN} with the slot index in the NBT
 * additions payload ({@code {"p": pos}}); value labels are non-action
 * buttons (no click action). The listener mutates the per-player digit
 * array and re-renders.
 *
 * <p>Mirrors AuthMeBia's {@code AuthInput.buildSliderDialog}, adapted to
 * AuthMeUI's event-driven (key + NBT) click model.
 */
public class SliderDialogBuilder {

    private final SettingsManager settings;

    public SliderDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    /**
     * @param digits current per-slot digit values (length == settings.getSliderLength()).
     * @param statusLine optional error/status line, or null.
     */
    public Dialog construct(Player player, int[] digits, String statusLine) {
        return build(digits, statusLine, player);
    }

    public Dialog constructForAudience(int[] digits, String statusLine) {
        return build(digits, statusLine, null);
    }

    private Dialog build(int[] digits, String statusLine, Player player) {
        int length = settings.getSliderLength();
        int width = settings.getSliderButtonWidth();

        List<ActionButton> buttons = new ArrayList<>(length * 2 + 2);

        for (int i = 0; i < length; i++) {
            buttons.add(slotButton("▲", DialogIdentifiers.SLIDER_UP, i, width, player));
        }
        for (int i = 0; i < length; i++) {
            int v = digits[i];
            buttons.add(ActionButton.builder(Component.text(Integer.toString(v)))
                    .width(width)
                    .build());
        }
        for (int i = 0; i < length; i++) {
            buttons.add(slotButton("▼", DialogIdentifiers.SLIDER_DOWN, i, width, player));
        }
        buttons.add(ActionButton.builder(settings.getSliderConfirmButton())
                .action(DialogAction.customClick(DialogIdentifiers.SLIDER_CONFIRM, null))
                .build());

        ActionButton cancelButton = ActionButton.builder(settings.getSliderLogoutButton())
                .action(DialogAction.customClick(DialogIdentifiers.LOGIN_CANCEL, null))
                .build();

        List<DialogBody> body = new ArrayList<>();
        for (String line : settings.getSliderBodyRaw()) {
            body.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }
        if (statusLine != null && !statusLine.isBlank()) {
            body.add(DialogBody.plainMessage(settings.parseText(statusLine, player)));
        }

        DialogBase dialogBase = DialogBase.builder(settings.getSliderTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(body)
                .inputs(List.of())
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(buttons, cancelButton, length));
        });
    }

    private ActionButton slotButton(String glyph, net.kyori.adventure.key.Key key,
                                    int pos, int width, Player player) {
        BinaryTagHolder payload = PinDialogBuilder.nbtPayload("p", pos);
        return ActionButton.builder(Component.text(glyph))
                .width(width)
                .action(DialogAction.customClick(key, payload))
                .build();
    }
}
