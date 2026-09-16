package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.webview.util.PlatformUtil;
import dev.ivchenko.webview.windows.binding.Advapi32;
import dev.ivchenko.webview.windows.binding.Com;
import dev.ivchenko.webview.windows.binding.ComCallback;
import dev.ivchenko.webview.windows.binding.Kernel32;
import dev.ivchenko.webview.windows.binding.Ole32;
import dev.ivchenko.webview.windows.binding.Shlwapi;
import dev.ivchenko.webview.windows.binding.User32;
import dev.ivchenko.webview.windows.binding.WebView2;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

class WindowsNativeImageMetadataTest extends NativeImageMetadataContractTest {
    @BeforeEach
    void requireWindows() {
        Assumptions.assumeTrue(PlatformUtil.isWindows(), "Binds Win32 libraries: Windows only");
    }

    @Override
    protected String metadataPath() {
        return "META-INF/native-image/dev.ivchenko.webview/webview-jvm-windows/reachability-metadata.json";
    }

    @Override
    protected List<Class<?>> bindingClasses() {
        return List.of(Kernel32.class, User32.class, Ole32.class, Shlwapi.class, Advapi32.class, Com.class,
                ComCallback.class, WebView2.class, WindowsDispatcher.class, WindowsWebviewBackend.class);
    }
}
