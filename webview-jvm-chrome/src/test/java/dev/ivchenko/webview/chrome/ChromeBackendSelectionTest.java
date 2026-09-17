package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class ChromeBackendSelectionTest extends BackendSelectionContractTest {
    @Override
    protected Class<? extends WebviewBackendProvider> providerType() {
        return ChromeWebviewBackendProvider.class;
    }

    @Override
    protected String providerName() {
        return ChromeWebviewBackendProvider.NAME;
    }

    @Override
    protected boolean isThisPlatform() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }
}
