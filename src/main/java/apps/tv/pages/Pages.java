package apps.tv.pages;

/** Screens the TV app can be on — detected by {@link Navigator} to drive navigation. */
public enum Pages {
    MAIN,             // TvMainActivity (connect screen)
    WELCOME,          // TvWelcomeActivity (Sign in / Sign up)
    SIGN_IN,          // TvSignInActivity (QR + device code)
    SIGN_UP,          // TvSignUpActivity (QR + "Sign In Instead")
    SERVER_LIST,      // TvServerListActivity
    RECONNECT_DIALOG, // "Do you want to reconnect with the new protocol?"
    INFO_SCREEN,      // settings popup / Help & Support / Privacy Notice / Terms — has a "Go back"
    LOADING,          // splash / loading, nothing actionable yet
    UNKNOWN
}
