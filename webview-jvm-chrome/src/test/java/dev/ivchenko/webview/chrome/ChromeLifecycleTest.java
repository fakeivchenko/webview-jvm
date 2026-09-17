package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.LifecycleContractTest;

class ChromeLifecycleTest extends LifecycleContractTest {
    @Override
    protected boolean isThisPlatform() {
        return ChromeLocator.find().isPresent();
    }
}
