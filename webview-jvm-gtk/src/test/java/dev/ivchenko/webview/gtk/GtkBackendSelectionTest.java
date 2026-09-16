package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class GtkBackendSelectionTest extends BackendSelectionContractTest {
    @Override
    protected Class<? extends WebviewBackendProvider> providerType() {
        return GtkWebviewBackendProvider.class;
    }

    @Override
    protected String providerName() {
        return "gtk3-webkit2gtk-4.1";
    }

    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isUnixDesktop();
    }
}
