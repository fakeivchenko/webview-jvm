package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class MacBackendSelectionTest extends BackendSelectionContractTest {
    @Override
    protected Class<? extends WebviewBackendProvider> providerType() {
        return MacWebviewBackendProvider.class;
    }

    @Override
    protected String providerName() {
        return "cocoa-wkwebview";
    }

    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isMacOs();
    }
}
