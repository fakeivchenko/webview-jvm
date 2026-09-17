package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.testing.contract.BridgeContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class MacBridgeTest extends BridgeContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isMacOs();
    }
}
