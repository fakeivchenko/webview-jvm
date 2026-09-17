package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.util.PlatformUtil;
import lombok.SneakyThrows;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Captures the screen while a display test has its window up, so CI can attach what the engine actually drew.
 *
 * <p>Off unless {@code -Dwebview.screenshots=true}: a developer's own desktop is not worth photographing. The whole
 * virtual screen is taken rather than the window alone, because on Xvfb the window is the only thing there and on a
 * real desktop the surroundings help explain a failure. {@code java.awt.Robot} needs nothing beyond the X libraries
 * WebKitGTK already pulls in, plus {@code libXtst}. On macOS the system's {@code screencapture} is used instead: AWT
 * would bring a second {@code NSApplication} into a process that already runs one.</p>
 */
public class Screenshots {
    private static final boolean ENABLED = Boolean.getBoolean("webview.screenshots");
    private static final Path DIRECTORY = Path.of(System.getProperty("webview.screenshotsDir", "build/screenshots"));
    private static final long PAINT_DELAY_MILLIS = 500;

    private Screenshots() {
    }

    /**
     * Saves {@code <name>.png} into the screenshot directory; a no-op when screenshots are off.
     *
     * <p>A capture that the desktop refuses (Wayland without a portal grant, a locked session) is reported, not thrown:
     * the picture is diagnostics, the test's assertions are the test. CI notices a missing picture through the artifact
     * step instead.</p>
     */
    @SneakyThrows
    public static void capture(String name) {
        if (!ENABLED) return;

        Thread.sleep(PAINT_DELAY_MILLIS);
        Files.createDirectories(DIRECTORY);
        Path file = DIRECTORY.resolve(name + ".png");
        if (PlatformUtil.isMacOs()) {
            new ProcessBuilder("screencapture", "-x", file.toString()).inheritIO().start().waitFor();
            return;
        }
        try {
            Rectangle screen = new Rectangle();
            for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                screen = screen.union(device.getDefaultConfiguration().getBounds());
            }
            ImageIO.write(new Robot().createScreenCapture(screen), "png", file.toFile());
        } catch (SecurityException | AWTException e) {
            System.err.println("Screenshot '" + name + "' skipped: " + e.getMessage());
        }
    }
}
