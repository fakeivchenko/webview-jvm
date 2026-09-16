package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.testing.contract.LifecycleContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class WindowsLifecycleTest extends LifecycleContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isWindows();
    }
}
