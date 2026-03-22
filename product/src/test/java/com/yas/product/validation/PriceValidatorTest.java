package com.yas.product.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceValidatorTest {

    private final PriceValidator priceValidator = new PriceValidator();

    @Test
    void isValid_acceptsZeroOrPositiveValues() {
        assertThat(priceValidator.isValid(0D, null)).isTrue();
        assertThat(priceValidator.isValid(12.5D, null)).isTrue();
    }

    @Test
    void isValid_rejectsNegativeValues() {
        assertThat(priceValidator.isValid(-1D, null)).isFalse();
    }
}
