package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.testing.contract.LifecycleContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class MacLifecycleTest extends LifecycleContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isMacOs();
    }
}
