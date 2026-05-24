package com.yas.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.product.model.Brand;
import com.yas.product.model.Category;
import com.yas.product.model.Product;
import com.yas.product.model.ProductCategory;
import com.yas.product.model.ProductImage;
import com.yas.product.model.ProductOption;
import com.yas.product.model.ProductOptionCombination;
import com.yas.product.model.attribute.ProductAttribute;
import com.yas.product.model.attribute.ProductAttributeGroup;
import com.yas.product.model.attribute.ProductAttributeValue;
import com.yas.product.repository.ProductOptionCombinationRepository;
import com.yas.product.repository.ProductRepository;
import com.yas.product.viewmodel.NoFileMediaVm;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductDetailServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private ProductOptionCombinationRepository productOptionCombinationRepository;

    private ProductDetailService productDetailService;

    @BeforeEach
    void setUp() {
        productDetailService = new ProductDetailService(productRepository, mediaService, productOptionCombinationRepository);
    }

    @Test
    void getProductDetailById_whenProductHasFullDetail_returnsDetailInfo() {
        Product product = product(1L, "Macbook", "macbook");
        product.setBrand(brand(10L, "Apple"));
        product.setProductCategories(List.of(productCategory(product, category(20L, "Laptop"))));
        product.setProductImages(List.of(productImage(product, 2L)));
        product.setAttributeValues(List.of(attributeValue(product)));
        product.setHasOptions(true);

        Product variation = product(2L, "Macbook 16GB", "macbook-16gb");
        variation.setThumbnailMediaId(3L);
        variation.setProductImages(List.of(productImage(variation, 4L)));
        product.setProducts(List.of(variation));

        ProductOption option = new ProductOption();
        option.setId(30L);
        option.setName("Memory");
        ProductOptionCombination combination = ProductOptionCombination.builder()
            .product(variation)
            .productOption(option)
            .value("16GB")
            .displayOrder(1)
            .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(mediaService.getMedia(1L)).thenReturn(media(1L, "thumb"));
        when(mediaService.getMedia(2L)).thenReturn(media(2L, "image"));
        when(mediaService.getMedia(3L)).thenReturn(media(3L, "variation-thumb"));
        when(mediaService.getMedia(4L)).thenReturn(media(4L, "variation-image"));
        when(productOptionCombinationRepository.findAllByProduct(variation)).thenReturn(List.of(combination));

        var detail = productDetailService.getProductDetailById(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getBrandId()).isEqualTo(10L);
        assertThat(detail.getCategories()).hasSize(1);
        assertThat(detail.getAttributeValues()).hasSize(1);
        assertThat(detail.getVariations()).hasSize(1);
        assertThat(detail.getThumbnail().url()).isEqualTo("thumb");
        assertThat(detail.getProductImages()).hasSize(1);
    }

    @Test
    void getProductDetailById_whenOptionalCollectionsAreNull_returnsEmptyDefaults() {
        Product product = product(1L, "Macbook", "macbook");
        product.setBrand(null);
        product.setProductCategories(null);
        product.setProductImages(null);
        product.setAttributeValues(new ArrayList<>());
        product.setProducts(new ArrayList<>());
        product.setThumbnailMediaId(null);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        var detail = productDetailService.getProductDetailById(1L);

        assertThat(detail.getBrandId()).isNull();
        assertThat(detail.getBrandName()).isNull();
        assertThat(detail.getCategories()).isEmpty();
        assertThat(detail.getProductImages()).isEmpty();
        assertThat(detail.getThumbnail()).isNull();
    }

    @Test
    void getProductDetailById_whenMissingOrUnpublished_throwsNotFoundException() {
        Product unpublished = product(2L, "Hidden", "hidden");
        unpublished.setPublished(false);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(2L)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> productDetailService.getProductDetailById(1L))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> productDetailService.getProductDetailById(2L))
            .isInstanceOf(NotFoundException.class);
    }

    private Product product(Long id, String name, String slug) {
        return Product.builder()
            .id(id)
            .name(name)
            .shortDescription("short")
            .description("description")
            .specification("spec")
            .sku("sku")
            .gtin("gtin")
            .slug(slug)
            .price(100.0)
            .isAllowedToOrder(true)
            .isPublished(true)
            .isFeatured(true)
            .isVisibleIndividually(true)
            .stockTrackingEnabled(true)
            .taxClassId(5L)
            .metaTitle("meta")
            .metaKeyword("keyword")
            .metaDescription("meta description")
            .thumbnailMediaId(1L)
            .productCategories(new ArrayList<>())
            .productImages(new ArrayList<>())
            .attributeValues(new ArrayList<>())
            .products(new ArrayList<>())
            .build();
    }

    private Brand brand(Long id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        return brand;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private ProductCategory productCategory(Product product, Category category) {
        return ProductCategory.builder().product(product).category(category).build();
    }

    private ProductImage productImage(Product product, Long imageId) {
        return ProductImage.builder().product(product).imageId(imageId).build();
    }

    private ProductAttributeValue attributeValue(Product product) {
        ProductAttributeGroup group = new ProductAttributeGroup();
        group.setId(1L);
        group.setName("Specs");
        ProductAttribute attribute = ProductAttribute.builder()
            .id(1L)
            .name("CPU")
            .productAttributeGroup(group)
            .build();
        ProductAttributeValue value = new ProductAttributeValue();
        value.setId(1L);
        value.setProduct(product);
        value.setProductAttribute(attribute);
        value.setValue("M3");
        return value;
    }

    private NoFileMediaVm media(Long id, String url) {
        return new NoFileMediaVm(id, "caption", "file", "image", url);
    }
}
