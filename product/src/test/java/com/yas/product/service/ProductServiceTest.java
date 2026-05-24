package com.yas.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.BadRequestException;
import com.yas.commonlibrary.exception.DuplicatedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.product.model.Brand;
import com.yas.product.model.Category;
import com.yas.product.model.Product;
import com.yas.product.model.ProductCategory;
import com.yas.product.model.ProductImage;
import com.yas.product.model.ProductOption;
import com.yas.product.model.ProductOptionCombination;
import com.yas.product.model.ProductOptionValue;
import com.yas.product.model.ProductRelated;
import com.yas.product.model.attribute.ProductAttribute;
import com.yas.product.model.attribute.ProductAttributeGroup;
import com.yas.product.model.attribute.ProductAttributeValue;
import com.yas.product.model.enumeration.DimensionUnit;
import com.yas.product.model.enumeration.FilterExistInWhSelection;
import com.yas.product.repository.BrandRepository;
import com.yas.product.repository.CategoryRepository;
import com.yas.product.repository.ProductCategoryRepository;
import com.yas.product.repository.ProductImageRepository;
import com.yas.product.repository.ProductOptionCombinationRepository;
import com.yas.product.repository.ProductOptionRepository;
import com.yas.product.repository.ProductOptionValueRepository;
import com.yas.product.repository.ProductRelatedRepository;
import com.yas.product.repository.ProductRepository;
import com.yas.product.viewmodel.NoFileMediaVm;
import com.yas.product.viewmodel.product.ProductOptionValueDisplay;
import com.yas.product.viewmodel.product.ProductPostVm;
import com.yas.product.viewmodel.product.ProductPutVm;
import com.yas.product.viewmodel.product.ProductQuantityPostVm;
import com.yas.product.viewmodel.product.ProductQuantityPutVm;
import com.yas.product.viewmodel.product.ProductVariationPostVm;
import com.yas.product.viewmodel.product.ProductVariationPutVm;
import com.yas.product.viewmodel.productoption.ProductOptionValuePostVm;
import com.yas.product.viewmodel.productoption.ProductOptionValuePutVm;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private ProductCategoryRepository productCategoryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductOptionRepository productOptionRepository;
    @Mock
    private ProductOptionValueRepository productOptionValueRepository;
    @Mock
    private ProductOptionCombinationRepository productOptionCombinationRepository;
    @Mock
    private ProductRelatedRepository productRelatedRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
            productRepository,
            mediaService,
            brandRepository,
            productCategoryRepository,
            categoryRepository,
            productImageRepository,
            productOptionRepository,
            productOptionValueRepository,
            productOptionCombinationRepository,
            productRelatedRepository
        );
    }

    @Test
    void createProduct_whenBasicProductIsValid_persistsProductAssociations() {
        ProductPostVm request = productPostVm(List.of(), List.of(), List.of(), List.of(99L), List.of(5L, 6L));
        Brand brand = brand(10L, "Apple");
        Category category = category(20L, "Phones");
        Product related = product(99L, "Related", "related");

        when(productRepository.findBySlugAndIsPublishedTrue("iphone")).thenReturn(Optional.empty());
        when(productRepository.findByGtinAndIsPublishedTrue("gtin-main")).thenReturn(Optional.empty());
        when(productRepository.findBySkuAndIsPublishedTrue("sku-main")).thenReturn(Optional.empty());
        when(productRepository.findAllById(List.of())).thenReturn(List.of());
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(categoryRepository.findAllById(List.of(20L))).thenReturn(List.of(category));
        when(productRepository.findAllById(List.of(99L))).thenReturn(List.of(related));

        var response = productService.createProduct(request);

        assertThat(response.id()).isEqualTo(1L);
        verify(productCategoryRepository).saveAll(anyList());
        verify(productImageRepository).saveAll(anyList());
        verify(productRelatedRepository).saveAll(anyList());
    }

    @Test
    void createProduct_whenProductHasVariations_createsOptionValuesAndCombinations() {
        ProductVariationPostVm variation = new ProductVariationPostVm(
            "iPhone Blue", "IPHONE-BLUE", "sku-blue", "gtin-blue", 1100.0, 8L, List.of(9L), Map.of(1L, "Blue"));
        ProductOptionValuePostVm optionValue = new ProductOptionValuePostVm(1L, "text", 1, List.of("Blue"));
        ProductOptionValueDisplay display = new ProductOptionValueDisplay(1L, "text", 1, "Blue");
        ProductPostVm request = productPostVm(List.of(variation), List.of(optionValue), List.of(display), List.of(), List.of());
        ProductOption option = productOption(1L, "Color");
        Product savedVariation = product(2L, "iPhone Blue", "iphone-blue");
        ProductOptionValue savedOptionValue = ProductOptionValue.builder()
            .id(3L)
            .productOption(option)
            .displayOrder(1)
            .displayType("text")
            .value("Blue")
            .build();

        when(productRepository.findBySlugAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findByGtinAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findBySkuAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findAllById(List.of())).thenReturn(List.of());
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand(10L, "Apple")));
        when(categoryRepository.findAllById(List.of(20L))).thenReturn(List.of(category(20L, "Phones")));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(saved.getId() == null ? 1L : saved.getId());
            return saved;
        });
        when(productRepository.saveAll(anyList())).thenReturn(List.of(savedVariation));
        when(productOptionRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(option));
        when(productOptionValueRepository.saveAll(anyList())).thenReturn(List.of(savedOptionValue));

        productService.createProduct(request);

        verify(productOptionValueRepository).saveAll(anyList());
        verify(productOptionCombinationRepository).saveAll(anyList());
    }

    @Test
    void createProduct_whenValidationFails_throwsExpectedException() {
        ProductPostVm baseRequest = productPostVm(List.of(), List.of(), List.of(), List.of(), List.of());
        ProductPostVm invalidDimensions = new ProductPostVm(
            baseRequest.name(), baseRequest.slug(), baseRequest.brandId(),
            baseRequest.categoryIds(), baseRequest.shortDescription(), baseRequest.description(),
            baseRequest.specification(), baseRequest.sku(), baseRequest.gtin(),
            baseRequest.weight(), baseRequest.dimensionUnit(), 1.0, 2.0, baseRequest.height(),
            baseRequest.price(), baseRequest.isAllowedToOrder(), baseRequest.isPublished(),
            baseRequest.isFeatured(), baseRequest.isVisibleIndividually(),
            baseRequest.stockTrackingEnabled(), baseRequest.metaTitle(), baseRequest.metaKeyword(),
            baseRequest.metaDescription(), baseRequest.thumbnailMediaId(), baseRequest.productImageIds(),
            baseRequest.variations(), baseRequest.productOptionValues(),
            baseRequest.productOptionValueDisplays(), baseRequest.relatedProductIds(),
            baseRequest.taxClassId());

        assertThatThrownBy(() -> productService.createProduct(invalidDimensions))
            .isInstanceOf(BadRequestException.class);

        ProductPostVm duplicateSlug = productPostVm(List.of(), List.of(), List.of(), List.of(), List.of());
        when(productRepository.findBySlugAndIsPublishedTrue("iphone"))
            .thenReturn(Optional.of(product(7L, "Existing", "iphone")));

        assertThatThrownBy(() -> productService.createProduct(duplicateSlug))
            .isInstanceOf(DuplicatedException.class);
    }

    @Test
    void updateProduct_whenProductExists_updatesProductGraph() {
        Product existing = product(1L, "Old", "old");
        existing.setBrand(brand(9L, "Old brand"));
        existing.setProductCategories(new ArrayList<>(List.of(productCategory(existing, category(30L, "Old")))));
        existing.setProductImages(new ArrayList<>(List.of(productImage(existing, 55L))));
        Product existingVariant = product(2L, "Old Variant", "old-variant");
        existingVariant.setProductImages(new ArrayList<>());
        existing.setProducts(new ArrayList<>(List.of(existingVariant)));
        ProductRelated oldRelation = productRelated(existing, product(100L, "Old related", "old-related"));
        existing.setRelatedProducts(new ArrayList<>(List.of(oldRelation)));

        ProductVariationPutVm oldVariantVm = new ProductVariationPutVm(
            2L, "Updated Variant", "UPDATED-VARIANT", "sku-var", "gtin-var", 120.0, 12L, List.of(13L), Map.of());
        ProductVariationPutVm newVariantVm = new ProductVariationPutVm(
            null, "New Variant", "NEW-VARIANT", "sku-new", "gtin-new", 130.0, 14L, List.of(15L), Map.of(1L, "XL"));
        ProductOptionValuePutVm optionValue = new ProductOptionValuePutVm(1L, "text", 1, List.of("XL"));
        ProductOptionValueDisplay display = new ProductOptionValueDisplay(1L, "text", 1, "XL");
        ProductPutVm request = productPutVm(List.of(oldVariantVm, newVariantVm), List.of(optionValue), List.of(display));
        ProductOption option = productOption(1L, "Size");
        Product newSavedVariation = product(3L, "New Variant", "new-variant");
        ProductOptionValue savedOptionValue = ProductOptionValue.builder().productOption(option).displayOrder(1).build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.findBySlugAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findByGtinAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findBySkuAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        when(productRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Long> idList = new ArrayList<>();
            ids.forEach(idList::add);
            if (idList.contains(2L)) {
                return List.of(existingVariant);
            }
            if (idList.contains(99L)) {
                return List.of(product(99L, "Related", "related"));
            }
            return List.of();
        });
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand(10L, "Apple")));
        when(categoryRepository.findAllById(List.of(20L))).thenReturn(List.of(category(20L, "Phones")));
        when(productCategoryRepository.findAllByProductId(1L)).thenReturn(List.of(productCategory(existing, category(30L, "Old"))));
        when(productOptionRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(option));
        when(productOptionValueRepository.saveAll(anyList())).thenReturn(List.of(savedOptionValue));
        when(productRepository.saveAll(anyList())).thenReturn(List.of(newSavedVariation));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(1L, request);

        assertThat(existing.getName()).isEqualTo("iPhone 15");
        assertThat(existingVariant.getName()).isEqualTo("Updated Variant");
        verify(productCategoryRepository).deleteAllInBatch(anyList());
        verify(productRelatedRepository).deleteAll(anyList());
        verify(productOptionCombinationRepository).saveAll(anyList());
    }

    @Test
    void queryMethods_returnViewModelsFromRepositoryData() {
        Product product = richProduct(1L, "iPhone", "iphone");
        Product related = richProduct(2L, "Case", "case");
        product.setRelatedProducts(List.of(productRelated(product, related)));
        PageImpl<Product> productPage = new PageImpl<>(List.of(product));
        PageImpl<ProductRelated> relatedPage = new PageImpl<>(List.of(productRelated(product, related)));

        when(mediaService.getMedia(1L)).thenReturn(new NoFileMediaVm(1L, "caption", "file", "image", "thumb-url"));
        when(productRepository.getProductsWithFilter(eq("iphone"), eq("Apple"), any(Pageable.class))).thenReturn(productPage);
        when(productRepository.getFeaturedProduct(any(Pageable.class))).thenReturn(productPage);
        when(productRepository.findByProductNameAndCategorySlugAndPriceBetween(
            eq("iphone"), eq("phones"), eq(1.0), eq(2000.0), any(Pageable.class))).thenReturn(productPage);
        when(productRepository.findAllPublishedProductsByIds(eq(List.of(1L)), any(Pageable.class))).thenReturn(productPage);
        when(productRepository.findProductForWarehouse("iphone", "sku", List.of(1L), "ALL")).thenReturn(List.of(product));
        when(productRepository.findByCategoryIdsIn(List.of(5L))).thenReturn(List.of(product));
        when(productRepository.findByBrandIdsIn(List.of(10L))).thenReturn(List.of(product));
        when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(product));
        when(productRepository.getExportingProducts("iphone", "Apple")).thenReturn(List.of(product));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRelatedRepository.findAllByProduct(eq(product), any(Pageable.class))).thenReturn(relatedPage);

        assertThat(productService.getProductsWithFilter(0, 10, "IPhone", "Apple").productContent()).hasSize(1);
        assertThat(productService.getListFeaturedProducts(0, 10).productList()).hasSize(1);
        assertThat(productService.getProductsByMultiQuery(0, 10, "IPhone", "phones", 1.0, 2000.0).productContent()).hasSize(1);
        assertThat(productService.getProductCheckoutList(0, 10, List.of(1L)).productCheckoutListVms()).hasSize(1);
        assertThat(productService.getProductsForWarehouse("iphone", "sku", List.of(1L), FilterExistInWhSelection.ALL)).hasSize(1);
        assertThat(productService.getProductByCategoryIds(List.of(5L))).hasSize(1);
        assertThat(productService.getProductByBrandIds(List.of(10L))).hasSize(1);
        assertThat(productService.getProductByIds(List.of(1L))).hasSize(1);
        assertThat(productService.exportProducts("IPhone", "Apple")).hasSize(1);
        assertThat(productService.getRelatedProductsBackoffice(1L)).hasSize(1);
        assertThat(productService.getRelatedProductsStorefront(1L, 0, 10).productContent()).hasSize(1);
    }

    @Test
    void productDetailMethods_mapImagesAttributesAndVariations() {
        Product product = richProduct(1L, "iPhone", "iphone");
        Product variant = richProduct(2L, "iPhone Blue", "iphone-blue");
        variant.setParent(product);
        variant.setProductImages(List.of(productImage(variant, 2L)));
        product.setHasOptions(true);
        product.setProducts(List.of(variant));

        ProductAttributeGroup group = new ProductAttributeGroup();
        group.setId(1L);
        group.setName("Specs");
        ProductAttribute attribute = ProductAttribute.builder().id(1L).name("CPU").productAttributeGroup(group).build();
        ProductAttributeValue attributeValue = new ProductAttributeValue();
        attributeValue.setProduct(product);
        attributeValue.setProductAttribute(attribute);
        attributeValue.setValue("A17");
        product.setAttributeValues(List.of(attributeValue));

        ProductOption option = productOption(1L, "Color");
        ProductOptionCombination combination = ProductOptionCombination.builder()
            .product(variant)
            .productOption(option)
            .value("Blue")
            .displayOrder(1)
            .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findById(2L)).thenReturn(Optional.of(variant));
        when(productRepository.findBySlugAndIsPublishedTrue("iphone")).thenReturn(Optional.of(product));
        when(mediaService.getMedia(1L)).thenReturn(new NoFileMediaVm(1L, "caption", "file", "image", "thumb-url"));
        when(mediaService.getMedia(2L)).thenReturn(new NoFileMediaVm(2L, "caption", "file", "image", "image-url"));
        when(productOptionCombinationRepository.findAllByProduct(variant)).thenReturn(List.of(combination));

        assertThat(productService.getProductById(1L).productImageMedias()).hasSize(1);
        assertThat(productService.getProductDetail("iphone").productAttributeGroups()).hasSize(1);
        assertThat(productService.getProductEsDetailById(1L).attributes()).containsExactly("CPU");
        assertThat(productService.getProductVariationsByParentId(1L)).hasSize(1);
        assertThat(productService.getProductSlug(2L).slug()).isEqualTo("iphone");
    }

    @Test
    void stockAndDeleteMethods_updateOnlyExpectedProducts() {
        Product tracked = product(1L, "Tracked", "tracked");
        tracked.setStockTrackingEnabled(true);
        tracked.setStockQuantity(10L);
        Product untracked = product(2L, "Untracked", "untracked");
        untracked.setStockTrackingEnabled(false);
        untracked.setStockQuantity(10L);

        when(productRepository.findAllByIdIn(List.of(1L, 1L, 2L))).thenReturn(List.of(tracked, untracked));
        productService.subtractStockQuantity(List.of(
            new ProductQuantityPutVm(1L, 4L),
            new ProductQuantityPutVm(1L, 20L),
            new ProductQuantityPutVm(2L, 5L)));
        assertThat(tracked.getStockQuantity()).isZero();
        assertThat(untracked.getStockQuantity()).isEqualTo(10L);

        when(productRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(tracked));
        productService.restoreStockQuantity(List.of(new ProductQuantityPutVm(1L, 3L)));
        assertThat(tracked.getStockQuantity()).isEqualTo(3L);

        when(productRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(tracked, untracked));
        productService.updateProductQuantity(List.of(new ProductQuantityPostVm(1L, 8L), new ProductQuantityPostVm(2L, 7L)));
        assertThat(tracked.getStockQuantity()).isEqualTo(8L);
        assertThat(untracked.getStockQuantity()).isEqualTo(7L);

        Product variant = product(3L, "Variant", "variant");
        variant.setParent(product(99L, "Parent", "parent"));
        ProductOptionCombination combination = ProductOptionCombination.builder().id(1L).product(variant).build();
        when(productRepository.findById(3L)).thenReturn(Optional.of(variant));
        when(productOptionCombinationRepository.findAllByProduct(variant)).thenReturn(List.of(combination));

        productService.deleteProduct(3L);

        assertThat(variant.isPublished()).isFalse();
        verify(productOptionCombinationRepository).deleteAll(List.of(combination));
    }

    @Test
    void notFoundMethods_throwNotFoundException() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(404L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> productService.deleteProduct(404L)).isInstanceOf(NotFoundException.class);
    }

    private ProductPostVm productPostVm(
        List<ProductVariationPostVm> variations,
        List<ProductOptionValuePostVm> optionValues,
        List<ProductOptionValueDisplay> displays,
        List<Long> relatedIds,
        List<Long> imageIds
    ) {
        return new ProductPostVm(
            "iPhone 15",
            "IPHONE",
            10L,
            new ArrayList<>(List.of(20L)),
            "short",
            "description",
            "specification",
            "sku-main",
            "gtin-main",
            1.0,
            DimensionUnit.CM,
            2.0,
            1.0,
            0.5,
            1000.0,
            true,
            true,
            true,
            true,
            true,
            "meta title",
            "keyword",
            "meta description",
            1L,
            imageIds,
            variations,
            optionValues,
            displays,
            relatedIds,
            4L
        );
    }

    private ProductPutVm productPutVm(
        List<ProductVariationPutVm> variations,
        List<ProductOptionValuePutVm> optionValues,
        List<ProductOptionValueDisplay> displays
    ) {
        return new ProductPutVm(
            "iPhone 15",
            "IPHONE",
            1000.0,
            true,
            true,
            true,
            true,
            true,
            10L,
            new ArrayList<>(List.of(20L)),
            "short",
            "description",
            "specification",
            "sku-main",
            "gtin-main",
            1.0,
            DimensionUnit.CM,
            2.0,
            1.0,
            0.5,
            "meta title",
            "keyword",
            "meta description",
            1L,
            List.of(56L),
            variations,
            optionValues,
            displays,
            List.of(99L),
            4L
        );
    }

    private Product richProduct(Long id, String name, String slug) {
        Product product = product(id, name, slug);
        product.setShortDescription("short");
        product.setDescription("description");
        product.setSpecification("spec");
        product.setSku("sku");
        product.setGtin("gtin");
        product.setAllowedToOrder(true);
        product.setPublished(true);
        product.setFeatured(true);
        product.setVisibleIndividually(true);
        product.setStockTrackingEnabled(true);
        product.setWeight(1.0);
        product.setDimensionUnit(DimensionUnit.CM);
        product.setLength(2.0);
        product.setWidth(1.0);
        product.setHeight(0.5);
        product.setPrice(999.0);
        product.setBrand(brand(10L, "Apple"));
        product.setTaxClassId(4L);
        product.setMetaTitle("meta");
        product.setMetaKeyword("keyword");
        product.setMetaDescription("meta description");
        product.setThumbnailMediaId(1L);
        product.setProductImages(List.of(productImage(product, 2L)));
        product.setProductCategories(List.of(productCategory(product, category(5L, "Phones"))));
        product.setCreatedOn(ZonedDateTime.now());
        return product;
    }

    private Product product(Long id, String name, String slug) {
        Product product = Product.builder()
            .id(id)
            .name(name)
            .slug(slug)
            .price(10.0)
            .isPublished(true)
            .isVisibleIndividually(true)
            .isAllowedToOrder(true)
            .isFeatured(true)
            .stockTrackingEnabled(true)
            .productCategories(new ArrayList<>())
            .productImages(new ArrayList<>())
            .attributeValues(new ArrayList<>())
            .relatedProducts(new ArrayList<>())
            .products(new ArrayList<>())
            .build();
        product.setCreatedOn(ZonedDateTime.now());
        return product;
    }

    private Brand brand(Long id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setSlug(name.toLowerCase());
        brand.setPublished(true);
        brand.setCreatedOn(ZonedDateTime.now());
        return brand;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase());
        return category;
    }

    private ProductOption productOption(Long id, String name) {
        ProductOption option = new ProductOption();
        option.setId(id);
        option.setName(name);
        return option;
    }

    private ProductCategory productCategory(Product product, Category category) {
        return ProductCategory.builder().product(product).category(category).build();
    }

    private ProductImage productImage(Product product, Long imageId) {
        return ProductImage.builder().product(product).imageId(imageId).build();
    }

    private ProductRelated productRelated(Product product, Product related) {
        return ProductRelated.builder().product(product).relatedProduct(related).build();
    }
}
