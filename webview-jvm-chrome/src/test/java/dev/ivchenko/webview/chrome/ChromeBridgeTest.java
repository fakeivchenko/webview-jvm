package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.BridgeContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class ChromeBridgeTest extends BridgeContractTest {
    @Override
    protected boolean isThisPlatform() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }
}
