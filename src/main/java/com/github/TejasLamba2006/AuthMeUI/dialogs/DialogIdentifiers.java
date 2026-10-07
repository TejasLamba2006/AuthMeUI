package com.github.TejasLamba2006.AuthMeUI.dialogs;

import net.kyori.adventure.key.Key;

public final class DialogIdentifiers {

    private DialogIdentifiers() {
    }

    public static final String NAMESPACE = "authmeui";

    public static final Key LOGIN_SUBMIT = Key.key(NAMESPACE, "action/login");

    public static final Key LOGIN_CANCEL = Key.key(NAMESPACE, "action/login_cancel");

    public static final Key LOGIN_FORGOT = Key.key(NAMESPACE, "action/login_forgot");

    public static final Key REGISTER_SUBMIT = Key.key(NAMESPACE, "action/register");

    public static final Key RULES_CONFIRM = Key.key(NAMESPACE, "action/rules_confirm");

    public static final Key SUPPORT_ACTION = Key.key(NAMESPACE, "action/support");

    // ponytail: feature action keys (D–N). Each new dialog button binds one of
    // these; DialogInteractionListener.resolveActionId maps them to sound ids.
    public static final Key CAPTCHA_SUBMIT = Key.key(NAMESPACE, "action/captcha");
    public static final Key TOTP_SUBMIT = Key.key(NAMESPACE, "action/totp");
    public static final Key TOTP_CANCEL = Key.key(NAMESPACE, "action/totp_cancel");
    public static final Key FORGOT_SUBMIT = Key.key(NAMESPACE, "action/forgot");
    public static final Key FORGOT_BACK = Key.key(NAMESPACE, "action/forgot_back");
    public static final Key FORGOT_VERIFY = Key.key(NAMESPACE, "action/forgot_verify");
    public static final Key FORGOT_RESEND = Key.key(NAMESPACE, "action/forgot_resend");
    public static final Key RECOVER_SUBMIT = Key.key(NAMESPACE, "action/recover");
    public static final Key EMAIL_VERIFY = Key.key(NAMESPACE, "action/email_verify");
    public static final Key EMAIL_RESEND = Key.key(NAMESPACE, "action/email_resend");
    public static final Key PIN_INPUT = Key.key(NAMESPACE, "action/pin");
    public static final Key PIN_DELETE = Key.key(NAMESPACE, "action/pin_delete");
    public static final Key PIN_CONFIRM = Key.key(NAMESPACE, "action/pin_confirm");
    public static final Key SLIDER_UP = Key.key(NAMESPACE, "action/slider_up");
    public static final Key SLIDER_DOWN = Key.key(NAMESPACE, "action/slider_down");
    public static final Key SLIDER_CONFIRM = Key.key(NAMESPACE, "action/slider_confirm");

    // ponytail: feature J custom screens. ONE key for all custom-screen
    // button clicks; the screen index + button index ride in the NBT
    // additions payload so the listener can look up the exact screen/button
    // without per-button keys.
    public static final Key CUSTOM_SCREEN_BUTTON = Key.key(NAMESPACE, "action/screen_button");
    public static final String CUSTOM_SCREEN_DISMISS_KEY = "dismiss_forever";
}
