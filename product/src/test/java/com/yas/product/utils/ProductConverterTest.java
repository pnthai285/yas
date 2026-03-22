package com.yas.product.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductConverterTest {

    @Test
    void toSlug_trimsAndNormalizesInput() {
        String input = "  Fancy Product Name  ";

        String result = ProductConverter.toSlug(input);

        assertThat(result).isEqualTo("fancy-product-name");
    }
}
