package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.testing.contract.WindowContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class WindowsWindowTest extends WindowContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isWindows();
    }

    @Override
    protected Class<? extends WebviewBackend> expectedBackendType() {
        return WindowsWebviewBackend.class;
    }
}
