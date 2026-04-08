package com.github.TejasLamba2006.AuthMeUI.dialogs;

import net.kyori.adventure.key.Key;

public final class DialogIdentifiers {

    private DialogIdentifiers() {
    }

    public static final String NAMESPACE = "authmeui";

    public static final Key LOGIN_SUBMIT = Key.key(NAMESPACE, "action/login");

    public static final Key LOGIN_CANCEL = Key.key(NAMESPACE, "action/login_cancel");

    public static final Key REGISTER_SUBMIT = Key.key(NAMESPACE, "action/register");

    public static final Key RULES_CONFIRM = Key.key(NAMESPACE, "action/rules_confirm");

    public static final Key SUPPORT_ACTION = Key.key(NAMESPACE, "action/support");
}
