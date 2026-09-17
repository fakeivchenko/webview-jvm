package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.LifecycleContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class ChromeLifecycleTest extends LifecycleContractTest {
    @Override
    protected boolean isThisPlatform() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }
}
