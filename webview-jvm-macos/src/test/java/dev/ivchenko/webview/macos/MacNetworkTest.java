package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.testing.contract.NetworkContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class MacNetworkTest extends NetworkContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isMacOs();
    }
}
