package com.github.TejasLamba2006.AuthMeUI.dialogs;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A data-driven custom dialog screen (feature J). Holds RAW config strings;
 * the builder ({@link CustomScreenDialogBuilder}) parses them with player
 * context at render time so {@code {player}} and {@code <icons:name>} resolve
 * to the actual viewer.
 *
 * <p>Mirrors AuthMeBia's {@code dialog.customscreen.CustomScreen}, adapted
 * to AuthMeUI's event-driven (key + NBT) click model — buttons bind the
 * same {@link DialogIdentifiers#CUSTOM_SCREEN_BUTTON} key with the screen
 * index and button index in the NBT additions payload.
 */
public final class CustomScreen {

    public enum Trigger {
        COMMAND, POSTJOIN, PREJOIN;

        public static Trigger parse(String raw) {
            if (raw == null) return COMMAND;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "postjoin" -> POSTJOIN;
                case "prejoin" -> PREJOIN;
                default -> COMMAND;
            };
        }
    }

    public enum CheckboxAction {
        CLOSE, SHOW;

        public static CheckboxAction parse(String raw) {
            if (raw == null) return SHOW;
            return "close".equalsIgnoreCase(raw.trim()) ? CLOSE : SHOW;
        }
    }

    public record ButtonRaw(String label, Action action, String value, int width, String sound) {
        public enum Action {
            CLOSE, OPEN_URL, COPY, COMMAND, CONSOLE;

            public static Action parse(String raw) {
                if (raw == null) return CLOSE;
                return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                    case "open_url" -> OPEN_URL;
                    case "copy" -> COPY;
                    case "command" -> COMMAND;
                    case "console" -> CONSOLE;
                    default -> CLOSE;
                };
            }
        }
    }

    private final String id;
    private final boolean enabled;
    private final String titleRaw;
    private final List<String> contentRaw;
    private final boolean allowClose;
    private final int buttonWidth;
    private final List<ButtonRaw> buttons;
    private final Trigger trigger;
    private final String soundOnShow;
    private final String iconName;
    private final String checkboxLabelRaw;
    private final CheckboxAction checkboxAction;

    public CustomScreen(String id, boolean enabled, String titleRaw, List<String> contentRaw,
                        boolean allowClose, int buttonWidth, List<ButtonRaw> buttons,
                        Trigger trigger, String soundOnShow, String iconName,
                        String checkboxLabelRaw, CheckboxAction checkboxAction) {
        this.id = id;
        this.enabled = enabled;
        this.titleRaw = titleRaw;
        this.contentRaw = contentRaw;
        this.allowClose = allowClose;
        this.buttonWidth = buttonWidth;
        this.buttons = buttons;
        this.trigger = trigger;
        this.soundOnShow = soundOnShow;
        this.iconName = iconName;
        this.checkboxLabelRaw = checkboxLabelRaw;
        this.checkboxAction = checkboxAction;
    }

    @SuppressWarnings("unchecked")
    public static CustomScreen parse(Map<?, ?> map, int defaultButtonWidth) {
        if (map == null || map.isEmpty()) return null;
        String id = str(map, "id", "");
        if (id.isBlank()) return null;
        boolean enabled = bool(map, "enabled", true);
        String title = str(map, "title", "Notice");
        List<String> content = map.get("content") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();
        boolean allowClose = bool(map, "allow_close", true);
        int buttonWidth = intVal(map, "button_width", defaultButtonWidth);
        Trigger trigger = Trigger.parse(str(map, "trigger", "command"));
        String soundOnShow = str(map, "sound_on_show", "");
        String iconName = str(map, "icon", "");
        String checkboxLabelRaw = str(map, "checkbox_label", "");
        CheckboxAction checkboxAction = CheckboxAction.parse(str(map, "checkbox_action", "show"));

        List<ButtonRaw> buttons = new java.util.ArrayList<>();
        Object buttonsObj = map.get("buttons");
        if (buttonsObj instanceof List<?> bl) {
            int idx = 0;
            for (Object o : bl) {
                if (o instanceof Map<?, ?> bm) {
                    String label = str(bm, "label", "Button " + (idx + 1));
                    ButtonRaw.Action action = ButtonRaw.Action.parse(str(bm, "action", "close"));
                    String value = str(bm, "value", "");
                    int width = intVal(bm, "width", buttonWidth);
                    String sound = str(bm, "sound", "");
                    buttons.add(new ButtonRaw(label, action, value, width, sound));
                }
                idx++;
            }
        }

        return new CustomScreen(id, enabled, title, content, allowClose, buttonWidth,
                buttons, trigger, soundOnShow, iconName.isEmpty() ? null : iconName,
                checkboxLabelRaw.isBlank() ? null : checkboxLabelRaw, checkboxAction);
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String titleRaw() { return titleRaw; }
    public List<String> contentRaw() { return contentRaw; }
    public boolean allowClose() { return allowClose; }
    public int buttonWidth() { return buttonWidth; }
    public List<ButtonRaw> buttons() { return buttons; }
    public Trigger trigger() { return trigger; }
    public String soundOnShow() { return soundOnShow; }
    public String iconName() { return iconName; }
    public String checkboxLabelRaw() { return checkboxLabelRaw; }
    public CheckboxAction checkboxAction() { return checkboxAction; }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static boolean bool(Map<?, ?> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intVal(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException e) { return def; }
    }
}
