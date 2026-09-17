package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.util.PlatformUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/**
 * Gate for tests that open a window.
 *
 * <p>Without a display such a test is skipped, so a developer on a headless box still gets a green build. CI must never
 * take that shortcut: a skipped display test there is a false pass, so with {@code -Dwebview.requireDisplay=true} the
 * missing display is a failure instead.</p>
 */
public class DisplayAssumptions {
    public static final String REQUIRE_DISPLAY_PROPERTY = "webview.requireDisplay";

    private DisplayAssumptions() {
    }

    public static void assumeDisplay() {
        boolean present = PlatformUtil.isWindows() || PlatformUtil.isMacOs()
                || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
        if (!present && Boolean.getBoolean(REQUIRE_DISPLAY_PROPERTY)) {
            Assertions.fail("No display, but -D" + REQUIRE_DISPLAY_PROPERTY + "=true: run under xvfb-run");
        }
        Assumptions.assumeTrue(present, "No X11/Wayland display available");
    }
}
