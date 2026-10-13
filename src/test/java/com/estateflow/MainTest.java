package com.estateflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Phase 0 smoke test: the launcher class loads and Mockito can mock an interface. */
class MainTest {

    @Test
    void launcher_isLoadable() {
        assertTrue(Launcher.class.getName().endsWith("Launcher"));
    }

    @Test
    void mockito_canMockAnInterface() {
        Runnable r = mock(Runnable.class);
        r.run();
        org.mockito.Mockito.verify(r).run();
    }
}
