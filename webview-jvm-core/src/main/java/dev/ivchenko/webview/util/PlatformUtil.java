package dev.ivchenko.webview.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;

/** Coarse operating system detection, used by backend providers to rule themselves out cheaply. */
@UtilityClass
public class PlatformUtil {
    private final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    /** Windows, any edition. */
    public boolean isWindows() {
        return OS.contains("win");
    }

    /** macOS. */
    public boolean isMacOs() {
        return OS.contains("mac") || OS.contains("darwin");
    }

    /** Linux, any distribution. */
    public boolean isLinux() {
        return OS.contains("linux");
    }

    /** Linux and the BSDs - the systems where a GTK/Qt desktop stack is the norm. */
    public boolean isUnixDesktop() {
        return isLinux() || OS.contains("bsd") || OS.contains("sunos") || OS.contains("aix");
    }

    /** The raw {@code os.name}, for diagnostics. */
    public String osName() {
        return System.getProperty("os.name", "");
    }
}
