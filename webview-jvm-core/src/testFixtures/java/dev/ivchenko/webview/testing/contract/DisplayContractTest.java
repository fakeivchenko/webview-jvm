package dev.ivchenko.webview.testing.contract;

import dev.ivchenko.webview.testing.DisplayAssumptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base of every contract that opens a window: runs only on the module's own platform, and only when that platform has a
 * display to draw on.
 */
public abstract class DisplayContractTest {
    /** Whether the machine running the tests is this module's platform. */
    protected abstract boolean isThisPlatform();

    @BeforeEach
    void requirePlatformAndDisplay() {
        Assumptions.assumeTrue(this.isThisPlatform(), "Not this backend's platform");
        DisplayAssumptions.assumeDisplay();
    }
}
