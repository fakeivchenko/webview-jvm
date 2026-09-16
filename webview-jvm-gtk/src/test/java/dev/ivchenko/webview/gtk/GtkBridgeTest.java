package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.testing.contract.BridgeContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class GtkBridgeTest extends BridgeContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isUnixDesktop();
    }
}
