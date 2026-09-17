package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.BridgeContractTest;

class ChromeBridgeTest extends BridgeContractTest {
    @Override
    protected boolean isThisPlatform() {
        return ChromeLocator.find().isPresent();
    }
}
