package com.yas.product.service;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.product.model.Brand;
import com.yas.product.model.Category;
import com.yas.product.model.Product;
import com.yas.product.model.ProductCategory;
import com.yas.product.model.ProductImage;
import com.yas.product.model.ProductOption;
import com.yas.product.model.ProductOptionCombination;
import com.yas.product.model.attribute.ProductAttribute;
import com.yas.product.model.attribute.ProductAttributeValue;
import com.yas.product.repository.ProductOptionCombinationRepository;
import com.yas.product.repository.ProductRepository;
import com.yas.product.viewmodel.NoFileMediaVm;
import com.yas.product.viewmodel.product.ProductDetailInfoVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDetailServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MediaService mediaService;

    @Mock
    private ProductOptionCombinationRepository productOptionCombinationRepository;

    @InjectMocks
    private ProductDetailService productDetailService;

    @Test
    void getProductDetailById_whenProductIsNotPublished_throwsNotFound() {
        Product product = Product.builder().id(1L).isPublished(false).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productDetailService.getProductDetailById(1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getProductDetailById_returnsAggregatedInformation() {
        Product product = Product.builder()
            .id(1L)
            .name("Main Product")
            .slug("main-product")
            .sku("sku-main")
            .gtin("gtin-main")
            .price(50D)
            .isPublished(true)
            .isAllowedToOrder(true)
            .isFeatured(false)
            .isVisibleIndividually(true)
            .stockTrackingEnabled(true)
            .metaTitle("meta-title")
            .metaKeyword("meta-keyword")
            .metaDescription("meta-description")
            .thumbnailMediaId(10L)
            .taxClassId(5L)
            .hasOptions(true)
            .build();

        Category category = new Category();
        category.setId(3L);
        category.setName("Accessories");
        ProductCategory productCategory = ProductCategory.builder()
            .product(product)
            .category(category)
            .build();
        product.setProductCategories(List.of(productCategory));

        Brand brand = new Brand();
        brand.setId(4L);
        brand.setName("Acme");
        product.setBrand(brand);

        ProductAttribute attribute = ProductAttribute.builder()
            .id(8L)
            .name("Material")
            .productAttributeGroup(null)
            .build();
        ProductAttributeValue attributeValue = new ProductAttributeValue();
        attributeValue.setId(6L);
        attributeValue.setProduct(product);
        attributeValue.setProductAttribute(attribute);
        attributeValue.setValue("Cotton");
        product.setAttributeValues(List.of(attributeValue));

        ProductImage productImage = ProductImage.builder()
            .id(11L)
            .imageId(20L)
            .product(product)
            .build();
        product.setProductImages(List.of(productImage));

        Product variation = Product.builder()
            .id(2L)
            .name("Red Variant")
            .slug("red-variant")
            .sku("sku-red")
            .gtin("gtin-red")
            .price(55D)
            .isPublished(true)
            .thumbnailMediaId(30L)
            .parent(product)
            .productImages(List.of(ProductImage.builder().imageId(40L).product(product).build()))
            .build();
        variation.getProductImages().forEach(image -> image.setProduct(variation));
        product.setProducts(List.of(variation));

        ProductOption option = new ProductOption();
        option.setId(9L);
        ProductOptionCombination combination = ProductOptionCombination.builder()
            .product(variation)
            .productOption(option)
            .value("Red")
            .displayOrder(1)
            .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productOptionCombinationRepository.findAllByProduct(variation)).thenReturn(List.of(combination));
        when(mediaService.getMedia(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return new NoFileMediaVm(id, "", "", "", "url-" + id);
        });

        ProductDetailInfoVm result = productDetailService.getProductDetailById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getVariations()).hasSize(1);
        assertThat(result.getBrandName()).isEqualTo("Acme");
        assertThat(result.getCategories()).extracting("name").containsExactly("Accessories");
        assertThat(result.getAttributeValues()).hasSize(1);
        assertThat(result.getProductImages()).hasSize(1);
        assertThat(result.getThumbnail().url()).isEqualTo("url-10");
        assertThat(result.getVariations().getFirst().options()).containsEntry(9L, "Red");
    }
}
