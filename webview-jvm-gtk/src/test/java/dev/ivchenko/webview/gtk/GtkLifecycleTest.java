package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.testing.contract.LifecycleContractTest;
import dev.ivchenko.webview.util.PlatformUtil;

class GtkLifecycleTest extends LifecycleContractTest {
    @Override
    protected boolean isThisPlatform() {
        return PlatformUtil.isUnixDesktop();
    }
}
