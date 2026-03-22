package com.yas.customer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AbstractCircuitBreakFallbackHandlerTest {

    private final TestFallbackHandler fallbackHandler = new TestFallbackHandler();

    @Test
    void handleError_rethrowsOriginalThrowable() {
        IllegalStateException exception = new IllegalStateException("failure");

        assertThatThrownBy(() -> fallbackHandler.callHandleError(exception))
            .isSameAs(exception);
    }

    @Test
    void handleTypedFallback_propagatesThrowable() {
        RuntimeException exception = new RuntimeException("typed");

        assertThatThrownBy(() -> fallbackHandler.callHandleTypedFallback(exception))
            .isSameAs(exception);
    }

    @Test
    void handleBodilessFallback_propagatesThrowable() {
        Exception exception = new Exception("bodiless");

        assertThatThrownBy(() -> fallbackHandler.callHandleBodilessFallback(exception))
            .isSameAs(exception);
    }

    private static class TestFallbackHandler extends AbstractCircuitBreakFallbackHandler {
        void callHandleError(Throwable throwable) throws Throwable {
            handleError(throwable);
        }

        void callHandleTypedFallback(Throwable throwable) throws Throwable {
            handleTypedFallback(throwable);
        }

        void callHandleBodilessFallback(Throwable throwable) throws Throwable {
            handleBodilessFallback(throwable);
        }
    }
}
