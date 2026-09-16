package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.testing.contract.BridgeContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class WindowsBridgeTest extends BridgeContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isWindows();
    }
}
