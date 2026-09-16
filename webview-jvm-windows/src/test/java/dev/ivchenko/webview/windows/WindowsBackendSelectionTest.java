package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class WindowsBackendSelectionTest extends BackendSelectionContractTest {
    @Override
    protected Class<? extends WebviewBackendProvider> providerType() {
        return WindowsWebviewBackendProvider.class;
    }

    @Override
    protected String providerName() {
        return "win32-webview2";
    }

    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isWindows();
    }
}
