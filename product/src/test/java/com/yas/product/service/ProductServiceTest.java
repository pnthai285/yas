package com.yas.product.service;

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
import com.yas.product.viewmodel.ImageVm;
import com.yas.product.viewmodel.product.ProductDetailGetVm;
import com.yas.product.viewmodel.product.ProductGetDetailVm;
import com.yas.product.viewmodel.product.ProductListVm;
import com.yas.product.viewmodel.product.ProductOptionValueDisplay;
import com.yas.product.viewmodel.product.ProductSlugGetVm;
import com.yas.product.viewmodel.product.ProductPostVm;
import com.yas.product.viewmodel.product.ProductQuantityPutVm;
import com.yas.product.viewmodel.product.ProductThumbnailGetVm;
import com.yas.product.viewmodel.product.ProductVariationPostVm;
import com.yas.product.viewmodel.product.ProductsGetVm;
import com.yas.product.viewmodel.productoption.ProductOptionValuePostVm;
import com.yas.product.viewmodel.productattribute.ProductAttributeGroupGetVm;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductCategoryRepository productCategoryRepository;
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

    @InjectMocks
    private ProductService productService;

    @Test
    void createProduct_lengthLessThanWidth_throwsBadRequestException() {
        ProductPostVm productPostVm = buildProductPostVm(1D, 2D, List.of());

        assertThatThrownBy(() -> productService.createProduct(productPostVm))
            .isInstanceOf(BadRequestException.class);

        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void createProduct_duplicateVariationSlug_throwsDuplicatedException() {
        ProductVariationPostVm variationA = new ProductVariationPostVm("v1", "duplicate", "sku-1", "gtin-1",
            10D, null, List.of(), Map.of());
        ProductVariationPostVm variationB = new ProductVariationPostVm("v2", "Duplicate", "sku-2", "gtin-2",
            12D, null, List.of(), Map.of());
        ProductPostVm productPostVm = buildProductPostVm(5D, 4D, List.of(variationA, variationB));

        assertThatThrownBy(() -> productService.createProduct(productPostVm))
            .isInstanceOf(DuplicatedException.class);
    }

    @Test
    void getLatestProducts_withPositiveCount_returnsConvertedList() {
        Product product = Product.builder()
            .id(1L)
            .name("Product")
            .slug("product")
            .isAllowedToOrder(true)
            .isPublished(true)
            .isFeatured(false)
            .isVisibleIndividually(true)
            .price(15D)
            .taxClassId(3L)
            .build();

        when(productRepository.getLatestProducts(any(Pageable.class))).thenReturn(List.of(product));

        List<ProductListVm> results = productService.getLatestProducts(2);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(product.getId());
        verify(productRepository).getLatestProducts(PageRequest.of(0, 2));
    }

    @Test
    void getLatestProducts_withNonPositiveCount_returnsEmptyList() {
        List<ProductListVm> results = productService.getLatestProducts(0);

        assertThat(results).isEmpty();
        verify(productRepository, never()).getLatestProducts(any(Pageable.class));
    }

    @Test
    void subtractStockQuantity_mergesDuplicatedItemsAndPreventsNegativeStock() {
        Product trackedProduct = Product.builder()
            .id(10L)
            .stockQuantity(5L)
            .stockTrackingEnabled(true)
            .build();

        when(productRepository.findAllByIdIn(anyList())).thenReturn(List.of(trackedProduct));

        productService.subtractStockQuantity(List.of(
            new ProductQuantityPutVm(10L, 3L),
            new ProductQuantityPutVm(10L, 10L)));

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());
        Product savedProduct = captor.getValue().getFirst();
        assertThat(savedProduct.getStockQuantity()).isZero();
    }

    @Test
    void getProductsByBrand_returnsThumbnails() {
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setSlug("brand-slug");
        brand.setName("Brand Name");
        Product product = Product.builder()
            .id(5L)
            .name("Product")
            .slug("product")
            .thumbnailMediaId(9L)
            .brand(brand)
            .build();

        when(brandRepository.findBySlug("brand-slug")).thenReturn(Optional.of(brand));
        when(productRepository.findAllByBrandAndIsPublishedTrueOrderByIdAsc(brand)).thenReturn(List.of(product));
        when(mediaService.getMedia(9L)).thenReturn(new NoFileMediaVm(9L, "", "", "", "thumb-url"));

        List<com.yas.product.viewmodel.product.ProductThumbnailVm> results =
            productService.getProductsByBrand("brand-slug");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().thumbnailUrl()).isEqualTo("thumb-url");
    }

    @Test
    void getProductsForWarehouse_delegatesToRepository() {
        Product product = Product.builder().id(21L).name("Chair").sku("SKU-1").build();
        when(productRepository.findProductForWarehouse("chair", "sku",
            List.of(21L), FilterExistInWhSelection.ALL.name())).thenReturn(List.of(product));

        List<com.yas.product.viewmodel.product.ProductInfoVm> results = productService.getProductsForWarehouse(
            "chair", "sku", List.of(21L), FilterExistInWhSelection.ALL);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(21L);
    }

    @Test
    void getProductsByMultiQuery_returnsPagedData() {
        Product product = Product.builder()
            .id(99L)
            .name("Table")
            .slug("table")
            .thumbnailMediaId(7L)
            .price(25D)
            .build();
        PageImpl<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 1), 1);

        when(productRepository.findByProductNameAndCategorySlugAndPriceBetween("name", "category", 1D, 30D,
            PageRequest.of(0, 2))).thenReturn(page);
        when(mediaService.getMedia(7L)).thenReturn(new NoFileMediaVm(7L, "", "", "", "image-url"));

        ProductsGetVm result = productService.getProductsByMultiQuery(0, 2, "name", "category", 1D, 30D);

        assertThat(result.productContent()).hasSize(1);
        assertThat(result.productContent().getFirst().thumbnailUrl()).isEqualTo("image-url");
    }

    @Test
    void createProduct_withVariationsOptionsAndRelations_persistsCombinations() {
        Brand brand = new Brand();
        brand.setId(2L);
        Category category = new Category();
        category.setId(7L);
        category.setSlug("chairs");

        ProductOption option = new ProductOption();
        option.setId(99L);
        option.setName("Color");

        Product relatedProduct = Product.builder().id(200L).name("Related").build();

        ProductVariationPostVm red = new ProductVariationPostVm(
            "Red Chair",
            "red-chair",
            "SKU-RED",
            "GTIN-RED",
            15D,
            50L,
            List.of(60L),
            Map.of(option.getId(), "Red")
        );

        ProductVariationPostVm blue = new ProductVariationPostVm(
            "Blue Chair",
            "blue-chair",
            "SKU-BLUE",
            "GTIN-BLUE",
            18D,
            51L,
            List.of(61L),
            Map.of(option.getId(), "Blue")
        );

        ProductOptionValuePostVm optionValue = new ProductOptionValuePostVm(option.getId(), "Text", 1, List.of("Red", "Blue"));
        ProductOptionValueDisplay optionValueDisplay = ProductOptionValueDisplay.builder()
            .productOptionId(option.getId())
            .displayType("Text")
            .displayOrder(1)
            .value("Color")
            .build();

        ProductPostVm postVm = new ProductPostVm(
            "Chair",
            "chair",
            brand.getId(),
            List.of(category.getId()),
            "short",
            "desc",
            "spec",
            "SKU-MAIN",
            "GTIN-MAIN",
            1D,
            DimensionUnit.CM,
            5D,
            4D,
            3D,
            20D,
            true,
            true,
            false,
            true,
            true,
            "metaTitle",
            "metaKeyword",
            "metaDescription",
            40L,
            List.of(41L),
            List.of(red, blue),
            List.of(optionValue),
            List.of(optionValueDisplay),
            List.of(relatedProduct.getId()),
            3L
        );

        lenient().when(productRepository.findBySlugAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        lenient().when(productRepository.findByGtinAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        lenient().when(productRepository.findBySkuAndIsPublishedTrue(any())).thenReturn(Optional.empty());
        lenient().when(productRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            if (ids.contains(relatedProduct.getId())) {
                return List.of(relatedProduct);
            }
            return List.of();
        });
        lenient().when(brandRepository.findById(brand.getId())).thenReturn(Optional.of(brand));
        lenient().when(categoryRepository.findAllById(postVm.categoryIds())).thenReturn(List.of(category));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId(1L);
            }
            return product;
        });
        lenient().when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productImageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productOptionRepository.findAllByIdIn(anyList())).thenReturn(List.of(option));
        lenient().when(productOptionValueRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productOptionCombinationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productCategoryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productRelatedRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(mediaService.getMedia(anyLong())).thenReturn(new NoFileMediaVm(1L, "", "", "", "http://img"));

        ProductGetDetailVm result = productService.createProduct(postVm);

        assertThat(result.id()).isEqualTo(1L);
        verify(productOptionCombinationRepository).saveAll(anyList());
    }

    @Test
    void getProductDetail_includesAttributesMediaAndCategories() {
        Brand brand = new Brand();
        brand.setId(5L);
        brand.setName("Yas Brand");

        Category category = new Category();
        category.setId(8L);
        category.setName("Furniture");
        ProductCategory productCategory = ProductCategory.builder()
            .product(null)
            .category(category)
            .build();

        ProductAttributeGroup group = new ProductAttributeGroup();
        group.setId(1L);
        group.setName("General");

        ProductAttribute attributeWithGroup = ProductAttribute.builder()
            .id(3L)
            .name("Material")
            .productAttributeGroup(group)
            .build();
        ProductAttribute attributeWithoutGroup = ProductAttribute.builder()
            .id(4L)
            .name("Note")
            .productAttributeGroup(null)
            .build();

        Product product = Product.builder()
            .id(10L)
            .name("Chair")
            .slug("chair")
            .sku("SKU1")
            .gtin("GTIN1")
            .price(25D)
            .length(10D)
            .width(5D)
            .height(3D)
            .weight(2D)
            .dimensionUnit(DimensionUnit.CM)
            .isAllowedToOrder(true)
            .isPublished(true)
            .isFeatured(false)
            .isVisibleIndividually(true)
            .stockTrackingEnabled(true)
            .description("desc")
            .shortDescription("short")
            .specification("spec")
            .metaTitle("metaTitle")
            .metaKeyword("metaKeyword")
            .metaDescription("metaDescription")
            .thumbnailMediaId(11L)
            .brand(brand)
            .productCategories(List.of(productCategory))
            .build();

        ProductImage image = ProductImage.builder().imageId(15L).product(product).build();
        product.setProductImages(List.of(image));

        ProductAttributeValue valueWithGroup = new ProductAttributeValue();
        valueWithGroup.setProduct(product);
        valueWithGroup.setProductAttribute(attributeWithGroup);
        valueWithGroup.setValue("Wood");

        ProductAttributeValue valueWithoutGroup = new ProductAttributeValue();
        valueWithoutGroup.setProduct(product);
        valueWithoutGroup.setProductAttribute(attributeWithoutGroup);
        valueWithoutGroup.setValue("Handle with care");

        product.setAttributeValues(List.of(valueWithGroup, valueWithoutGroup));
        productCategory.setProduct(product);

        when(productRepository.findBySlugAndIsPublishedTrue("chair")).thenReturn(Optional.of(product));
        when(mediaService.getMedia(11L)).thenReturn(new NoFileMediaVm(11L, "", "", "", "thumb-url"));
        when(mediaService.getMedia(15L)).thenReturn(new NoFileMediaVm(15L, "", "", "", "img-url"));

        ProductDetailGetVm result = productService.getProductDetail("chair");

        assertThat(result.id()).isEqualTo(product.getId());
        assertThat(result.brandName()).isEqualTo(brand.getName());
        assertThat(result.productCategories()).containsExactly(category.getName());
        assertThat(result.productImageMediaUrls()).containsExactly("img-url");
        assertThat(result.thumbnailMediaUrl()).isEqualTo("thumb-url");
        assertThat(result.productAttributeGroups().stream().map(ProductAttributeGroupGetVm::name).toList())
            .containsExactlyInAnyOrder("General", "None group");
    }

    @Test
    void getProductVariationsByParentId_returnsPublishedVariationsWithOptions() {
        Product parent = Product.builder()
            .id(50L)
            .hasOptions(true)
            .build();

        Product child = Product.builder()
            .id(51L)
            .name("Child")
            .slug("child")
            .sku("SKU-CHILD")
            .gtin("GTIN-CHILD")
            .price(12D)
            .thumbnailMediaId(30L)
            .parent(parent)
            .productImages(List.of(ProductImage.builder().imageId(31L).build()))
            .isPublished(true)
            .build();
        parent.setProducts(List.of(child));

        ProductOption option = new ProductOption();
        option.setId(10L);
        ProductOptionCombination combination = ProductOptionCombination.builder()
            .product(child)
            .productOption(option)
            .value("Red")
            .displayOrder(1)
            .build();

        when(productRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(productOptionCombinationRepository.findAllByProduct(child)).thenReturn(List.of(combination));
        when(mediaService.getMedia(30L)).thenReturn(new NoFileMediaVm(30L, "", "", "", "thumb"));
        when(mediaService.getMedia(31L)).thenReturn(new NoFileMediaVm(31L, "", "", "", "img"));

        List<com.yas.product.viewmodel.product.ProductVariationGetVm> result =
            productService.getProductVariationsByParentId(parent.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().options()).containsEntry(option.getId(), "Red");
        assertThat(result.getFirst().thumbnail().url()).isEqualTo("thumb");
        assertThat(result.getFirst().productImages().getFirst().url()).isEqualTo("img");
    }

    @Test
    void deleteProduct_removesOptionCombinationsWhenChild() {
        Product parent = Product.builder().id(70L).build();
        Product product = Product.builder().id(71L).parent(parent).isPublished(true).build();
        ProductOptionCombination optionCombination = ProductOptionCombination.builder().id(5L).product(product).build();

        when(productRepository.findById(71L)).thenReturn(Optional.of(product));
        when(productOptionCombinationRepository.findAllByProduct(product)).thenReturn(List.of(optionCombination));

        productService.deleteProduct(71L);

        verify(productOptionCombinationRepository).deleteAll(List.of(optionCombination));
        verify(productRepository).save(product);
        assertThat(product.isPublished()).isFalse();
    }

    @Test
    void getFeaturedProductsById_fallsBackToParentThumbnail() {
        Product parent = Product.builder().id(1L).thumbnailMediaId(90L).build();
        Product child = Product.builder().id(2L).name("Child").slug("child").price(5D).parent(parent)
            .thumbnailMediaId(80L).build();

        when(productRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(child));
        when(productRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(mediaService.getMedia(80L)).thenReturn(new NoFileMediaVm(80L, "", "", "", ""));
        when(mediaService.getMedia(90L)).thenReturn(new NoFileMediaVm(90L, "", "", "", "parent-url"));

        List<ProductThumbnailGetVm> result = productService.getFeaturedProductsById(List.of(2L));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().thumbnailUrl()).isEqualTo("parent-url");
    }

    @Test
    void getProductSlug_returnsParentSlugWhenExists() {
        Product parent = Product.builder().id(10L).slug("parent-slug").build();
        Product child = Product.builder().id(20L).slug("child-slug").parent(parent).build();

        when(productRepository.findById(child.getId())).thenReturn(Optional.of(child));

        ProductSlugGetVm result = productService.getProductSlug(child.getId());

        assertThat(result.slug()).isEqualTo(parent.getSlug());
        assertThat(result.productVariantId()).isEqualTo(child.getId());
    }

    @Test
    void getProductSlug_returnsSelfWhenNoParent() {
        Product product = Product.builder().id(30L).slug("solo").build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        ProductSlugGetVm result = productService.getProductSlug(product.getId());

        assertThat(result.slug()).isEqualTo("solo");
        assertThat(result.productVariantId()).isNull();
    }

    @Test
    void getProductsFromCategory_returnsPagedProducts() {
        Category category = new Category();
        category.setId(5L);
        category.setSlug("tables");
        Product product = Product.builder().id(6L).name("Table").slug("table").thumbnailMediaId(99L).build();
        ProductCategory productCategory = ProductCategory.builder().product(product).category(category).build();
        PageImpl<ProductCategory> page = new PageImpl<>(List.of(productCategory), PageRequest.of(0, 1), 1);

        when(categoryRepository.findBySlug("tables")).thenReturn(Optional.of(category));
        when(productCategoryRepository.findAllByCategory(PageRequest.of(0, 1), category)).thenReturn(page);
        when(mediaService.getMedia(99L)).thenReturn(new NoFileMediaVm(99L, "", "", "", "thumb-url"));

        var result = productService.getProductsFromCategory(0, 1, "tables");

        assertThat(result.productContent()).hasSize(1);
        assertThat(result.productContent().getFirst().thumbnailUrl()).isEqualTo("thumb-url");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getProductById_returnsDetailedVm() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Office");
        ProductCategory productCategory = ProductCategory.builder().category(category).build();
        ProductImage image = ProductImage.builder().imageId(100L).build();
        Brand brand = new Brand();
        brand.setId(4L);

        Product product = Product.builder()
            .id(2L)
            .name("Desk")
            .slug("desk")
            .sku("SKU-DESK")
            .gtin("GTIN-DESK")
            .thumbnailMediaId(50L)
            .productImages(List.of(image))
            .productCategories(List.of(productCategory))
            .brand(brand)
            .build();
        productCategory.setProduct(product);
        image.setProduct(product);

        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(mediaService.getMedia(50L)).thenReturn(new NoFileMediaVm(50L, "", "", "", "thumb"));
        when(mediaService.getMedia(100L)).thenReturn(new NoFileMediaVm(100L, "", "", "", "img"));

        var result = productService.getProductById(2L);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.thumbnailMedia().url()).isEqualTo("thumb");
        assertThat(result.productImageMedias()).extracting(ImageVm::url).containsExactly("img");
        assertThat(result.categories()).containsExactly(category);
    }

    @Test
    void getProductCheckoutList_mapsThumbnailUrl() {
        Brand brand = new Brand();
        brand.setId(9L);
        Product product = Product.builder()
            .id(7L)
            .name("Lamp")
            .slug("lamp")
            .price(12D)
            .thumbnailMediaId(200L)
            .isPublished(true)
            .isVisibleIndividually(true)
            .brand(brand)
            .build();

        PageImpl<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 1), 1);
        when(productRepository.findAllPublishedProductsByIds(List.of(7L), PageRequest.of(0, 1)))
            .thenReturn(page);
        when(mediaService.getMedia(200L)).thenReturn(new NoFileMediaVm(200L, "", "", "", "lamp-url"));

        var result = productService.getProductCheckoutList(0, 1, List.of(7L));

        assertThat(result.productCheckoutListVms()).hasSize(1);
        assertThat(result.productCheckoutListVms().getFirst().thumbnailUrl()).isEqualTo("lamp-url");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    private ProductPostVm buildProductPostVm(Double length, Double width, List<ProductVariationPostVm> variations) {
        return new ProductPostVm(
            "Product",
            "product",
            null,
            List.of(),
            "short",
            "description",
            "spec",
            "sku",
            "gtin",
            1D,
            DimensionUnit.CM,
            length,
            width,
            1D,
            10D,
            true,
            true,
            false,
            true,
            true,
            "metaTitle",
            "metaKeyword",
            "metaDescription",
            1L,
            List.of(),
            variations,
            List.of(),
            List.of(),
            List.of(),
            1L
        );
    }
}
