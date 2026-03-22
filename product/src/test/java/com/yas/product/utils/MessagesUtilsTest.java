package com.yas.product.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesUtilsTest {

    @Test
    void getMessage_returnsFormattedMessageWhenCodeExists() {
        String message = MessagesUtils.getMessage("PRODUCT_NOT_FOUND", 1L);

        assertThat(message).isEqualTo("Product 1 is not found");
    }

    @Test
    void getMessage_returnsCodeWhenMissing() {
        String message = MessagesUtils.getMessage("UNKNOWN_CODE", 99);

        assertThat(message).isEqualTo("UNKNOWN_CODE");
    }
}
