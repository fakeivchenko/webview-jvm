package dev.ivchenko.webview.testing.contract;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Backend discovery, without opening a window - so it also passes on a headless machine.
 *
 * <p>Each backend module states whether it is the one this machine should end up with. A module for another platform
 * still checks that it is registered and that it steps aside.</p>
 */
public abstract class BackendSelectionContractTest {
    /** The provider class this module registers in {@code META-INF/services}. */
    protected abstract Class<? extends WebviewBackendProvider> providerType();

    /** The provider's {@link WebviewBackendProvider#name()}. */
    protected abstract String providerName();

    /** Whether the machine running the tests is this module's platform. */
    protected abstract boolean isThisPlatform();

    @Test
    void providerIsOnTheServicePath() {
        List<WebviewBackendProvider> providers = Webview.providers();
        Assertions.assertTrue(providers.stream().anyMatch(this.providerType()::isInstance),
                this.providerType().getSimpleName() + " is not registered in META-INF/services: " + providers);
    }

    @Test
    void providerAnswersForItsOwnPlatformOnly() {
        WebviewBackendProvider provider = Webview.providers().stream()
                .filter(this.providerType()::isInstance)
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(this.isThisPlatform(), provider.isSupported());
        if (this.isThisPlatform()) {
            Assertions.assertEquals(this.providerName(), Webview.provider().orElseThrow().name());
        }
    }
}
