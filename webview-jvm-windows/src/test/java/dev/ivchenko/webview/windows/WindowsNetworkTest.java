package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.testing.contract.NetworkContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class WindowsNetworkTest extends NetworkContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isWindows();
    }
}
