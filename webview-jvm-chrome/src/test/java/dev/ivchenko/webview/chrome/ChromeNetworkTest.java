package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.NetworkContractTest;

class ChromeNetworkTest extends NetworkContractTest {
    @Override
    protected boolean isThisPlatform() {
        return ChromeLocator.find().isPresent();
    }
}
