package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.testing.contract.WindowContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class ChromeWindowTest extends WindowContractTest {
    @Override
    protected boolean isThisPlatform() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }

    @Override
    protected Class<? extends WebviewBackend> expectedBackendType() {
        return ChromeWebviewBackend.class;
    }
}
