package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.macos.binding.AppKit;
import dev.ivchenko.webview.macos.binding.Foundation;
import dev.ivchenko.webview.macos.binding.ObjC;
import dev.ivchenko.webview.macos.binding.WebKit;
import dev.ivchenko.webview.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.webview.util.PlatformUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

class MacNativeImageMetadataTest extends NativeImageMetadataContractTest {
    @BeforeEach
    void requireMacOs() {
        Assumptions.assumeTrue(PlatformUtil.isMacOs(), "Binds AppKit and WebKit: macOS only");
    }

    @Override
    protected String metadataPath() {
        return "META-INF/native-image/dev.ivchenko.webview/webview-jvm-macos/reachability-metadata.json";
    }

    @Override
    protected List<Class<?>> bindingClasses() {
        return List.of(ObjC.class, Foundation.class, AppKit.class, WebKit.class, MacDispatcher.class,
                MacWebviewBackend.class);
    }
}
