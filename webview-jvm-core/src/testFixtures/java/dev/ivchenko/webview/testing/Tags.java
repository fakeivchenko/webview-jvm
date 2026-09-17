package dev.ivchenko.webview.testing;

/** JUnit tag names; the Gradle tasks in this module select tests by them. */
public class Tags {
    /** Opens a real window: needs an X11 or Wayland display. */
    public static final String DISPLAY = "display";

    /** Reaches the public internet; excluded from every default run. */
    public static final String NETWORK = "network";

    private Tags() {
    }
}
