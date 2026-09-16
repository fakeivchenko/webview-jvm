package dev.ivchenko.webview.windows.binding;

import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds the installed WebView2 (Evergreen) runtime.
 *
 * <p>The official entry point is {@code WebView2Loader.dll} from the SDK, a native artifact this library refuses to
 * ship. The loader itself is thin: it reads the runtime's install location from the registry and loads
 * {@code EmbeddedBrowserWebView.dll} from there. That is all that is replicated here, and the same lookup answers the
 * cheap question "is WebView2 installed at all?" for backend selection.</p>
 */
@UtilityClass
public class WebView2Runtime {
    /** The runtime's Edge Update client id; its {@code ClientState} key records the install dir. */
    private final String CLIENT_STATE = "\\Microsoft\\EdgeUpdate\\ClientState\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";
    private final String INSTALL_DIRECTORY_VALUE = "EBWebView";
    private final String LIBRARY = "EBWebView\\x64\\EmbeddedBrowserWebView.dll";

    /** The runtime's core library, or empty when WebView2 is not installed. */
    public Optional<Path> library() {
        return Advapi32.readString(Advapi32.HKEY_LOCAL_MACHINE, "SOFTWARE\\WOW6432Node" + CLIENT_STATE, INSTALL_DIRECTORY_VALUE)
                .or(() -> Advapi32.readString(Advapi32.HKEY_CURRENT_USER, "SOFTWARE" + CLIENT_STATE, INSTALL_DIRECTORY_VALUE))
                .map(directory -> Path.of(directory, LIBRARY))
                .filter(Files::isRegularFile);
    }
}
