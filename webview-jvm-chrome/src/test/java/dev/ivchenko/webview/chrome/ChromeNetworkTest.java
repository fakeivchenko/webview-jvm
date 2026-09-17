package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.testing.contract.NetworkContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class ChromeNetworkTest extends NetworkContractTest {
    @Override
    protected boolean isThisPlatform() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }
}
