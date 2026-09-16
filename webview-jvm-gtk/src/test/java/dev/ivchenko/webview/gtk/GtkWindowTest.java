package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.testing.contract.WindowContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class GtkWindowTest extends WindowContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isUnixDesktop();
    }

    @Override
    protected Class<? extends WebviewBackend> expectedBackendType() {
        return GtkWebviewBackend.class;
    }
}
