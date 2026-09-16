package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.testing.contract.NetworkContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class GtkNetworkTest extends NetworkContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isUnixDesktop();
    }
}
