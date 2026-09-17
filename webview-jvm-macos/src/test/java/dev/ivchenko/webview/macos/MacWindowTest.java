package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.testing.contract.WindowContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class MacWindowTest extends WindowContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isMacOs();
    }

    @Override
    protected Class<? extends WebviewBackend> expectedBackendType() {
        return MacWebviewBackend.class;
    }
}
