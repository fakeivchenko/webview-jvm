package dev.ivchenko.webview.testing;

import lombok.SneakyThrows;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Captures the screen while a display test has its window up, so CI can attach what the engine actually drew.
 *
 * <p>Off unless {@code -Dwebview.screenshots=true}: a developer's own desktop is not worth photographing. The whole
 * virtual screen is taken rather than the window alone, because on Xvfb the window is the only thing there and on a
 * real desktop the surroundings help explain a failure. {@code java.awt.Robot} needs nothing beyond the X libraries
 * WebKitGTK already pulls in, plus {@code libXtst}.</p>
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
        try {
            Rectangle screen = new Rectangle();
            for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                screen = screen.union(device.getDefaultConfiguration().getBounds());
            }
            BufferedImage image = new Robot().createScreenCapture(screen);

            Files.createDirectories(DIRECTORY);
            ImageIO.write(image, "png", DIRECTORY.resolve(name + ".png").toFile());
        } catch (SecurityException | AWTException e) {
            System.err.println("Screenshot '" + name + "' skipped: " + e.getMessage());
        }
    }
}
