package com.yas.cart.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AbstractCircuitBreakFallbackHandlerTest {

    private final TestFallbackHandler handler = new TestFallbackHandler();

    @Test
    void handleBodilessFallback_shouldRethrowThrowable() {
        RuntimeException exception = new RuntimeException("boom");

        assertThatThrownBy(() -> handler.handleBodilessFallback(exception))
            .isSameAs(exception);
    }

    @Test
    void handleTypedFallback_shouldRethrowThrowable() {
        IllegalStateException exception = new IllegalStateException("fallback");

        assertThatThrownBy(() -> handler.handleTypedFallback(exception))
            .isSameAs(exception);
    }

    private static final class TestFallbackHandler extends AbstractCircuitBreakFallbackHandler {
    }
}