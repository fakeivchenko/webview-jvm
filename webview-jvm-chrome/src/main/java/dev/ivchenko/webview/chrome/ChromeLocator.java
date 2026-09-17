package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.util.PlatformUtil;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds a Chromium-based browser to drive: Google Chrome, Chromium or Microsoft Edge, in that order.
 *
 * <p>{@code -Dwebview.chrome=<path>} or {@code WEBVIEW_CHROME} names one explicitly and wins over the search; otherwise
 * the platform's usual install locations are tried, then the {@code PATH}. Edge counts because it is the same engine
 * with the same flags and the same protocol, and it is present on every Windows 10 and 11 machine.</p>
 */
@UtilityClass
public class ChromeLocator {
    /** System property naming the browser executable to use. */
    public final String CHROME_PROPERTY = "webview.chrome";

    /** Environment variable with the same meaning as {@link #CHROME_PROPERTY}. */
    public final String CHROME_VARIABLE = "WEBVIEW_CHROME";

    private final List<String> UNIX_NAMES = List.of(
            "google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge",
            "microsoft-edge-stable");

    /** The executable to launch, if any is installed. */
    public Optional<Path> find() {
        String explicit = System.getProperty(CHROME_PROPERTY, System.getenv(CHROME_VARIABLE));
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit.strip());
            return Files.isExecutable(path) ? Optional.of(path) : Optional.empty();
        }
        return candidates().stream().filter(Files::isExecutable).findFirst();
    }

    private List<Path> candidates() {
        List<Path> candidates = new ArrayList<>();
        if (PlatformUtil.isWindows()) {
            for (String root : List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
                String base = System.getenv(root);
                if (base == null) continue;
                candidates.add(Path.of(base, "Google", "Chrome", "Application", "chrome.exe"));
                candidates.add(Path.of(base, "Chromium", "Application", "chrome.exe"));
                candidates.add(Path.of(base, "Microsoft", "Edge", "Application", "msedge.exe"));
            }
        } else if (PlatformUtil.isMacOs()) {
            candidates.add(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
            candidates.add(Path.of("/Applications/Chromium.app/Contents/MacOS/Chromium"));
            candidates.add(Path.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"));
        }
        String path = System.getenv("PATH");
        if (path != null && !PlatformUtil.isWindows()) {
            for (String entry : path.split(File.pathSeparator))
                if (!entry.isBlank()) UNIX_NAMES.forEach(name -> candidates.add(Path.of(entry, name)));
        }
        return candidates;
    }
}
