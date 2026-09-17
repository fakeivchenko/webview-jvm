package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.contract.BackendSelectionContractTest;

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
        return ChromeLocator.find().isPresent();
    }
}
