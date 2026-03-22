package com.yas.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.BadRequestException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.commonlibrary.exception.StockExistingException;
import com.yas.inventory.model.Stock;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.model.enumeration.FilterExistInWhSelection;
import com.yas.inventory.repository.StockRepository;
import com.yas.inventory.repository.WarehouseRepository;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.product.ProductQuantityPostVm;
import com.yas.inventory.viewmodel.stock.StockPostVm;
import com.yas.inventory.viewmodel.stock.StockQuantityUpdateVm;
import com.yas.inventory.viewmodel.stock.StockQuantityVm;
import com.yas.inventory.viewmodel.stock.StockVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductService productService;
    @Mock
    private WarehouseService warehouseService;
    @Mock
    private StockHistoryService stockHistoryService;

    @InjectMocks
    private StockService stockService;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(2L).name("Main WH").addressId(10L).build();
    }

    @Test
    void addProductIntoWarehouse_whenStockAlreadyExists_shouldThrow() {
        StockPostVm request = new StockPostVm(1L, warehouse.getId());
        when(stockRepository.existsByWarehouseIdAndProductId(warehouse.getId(), request.productId()))
            .thenReturn(true);

        assertThrows(StockExistingException.class, () -> stockService.addProductIntoWarehouse(List.of(request)));

        verify(stockRepository, never()).saveAll(any());
    }

    @Test
    void addProductIntoWarehouse_whenProductMissing_shouldThrowNotFound() {
        StockPostVm request = new StockPostVm(3L, warehouse.getId());
        when(stockRepository.existsByWarehouseIdAndProductId(warehouse.getId(), request.productId()))
            .thenReturn(false);
        when(productService.getProduct(request.productId())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> stockService.addProductIntoWarehouse(List.of(request)));

        verify(stockRepository, never()).saveAll(any());
    }

    @Test
    void addProductIntoWarehouse_whenWarehouseMissing_shouldThrowNotFound() {
        StockPostVm request = new StockPostVm(4L, warehouse.getId());
        when(stockRepository.existsByWarehouseIdAndProductId(warehouse.getId(), request.productId()))
            .thenReturn(false);
        when(productService.getProduct(request.productId()))
            .thenReturn(new ProductInfoVm(request.productId(), "Name", "SKU", true));
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> stockService.addProductIntoWarehouse(List.of(request)));
    }

    @Test
    void addProductIntoWarehouse_whenValid_shouldPersistNewStocks() {
        StockPostVm request = new StockPostVm(5L, warehouse.getId());
        when(stockRepository.existsByWarehouseIdAndProductId(warehouse.getId(), request.productId()))
            .thenReturn(false);
        when(productService.getProduct(request.productId()))
            .thenReturn(new ProductInfoVm(request.productId(), "Prod", "SKU", true));
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        stockService.addProductIntoWarehouse(List.of(request));

        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).saveAll(captor.capture());
        Stock saved = captor.getValue().getFirst();
        assertThat(saved.getProductId()).isEqualTo(request.productId());
        assertThat(saved.getWarehouse()).isSameAs(warehouse);
        assertThat(saved.getQuantity()).isZero();
        assertThat(saved.getReservedQuantity()).isZero();
    }

    @Test
    void getStocksByWarehouseIdAndProductNameAndSku_shouldReturnMappedStockVm() {
        Long productId = 7L;
        ProductInfoVm productInfoVm = new ProductInfoVm(productId, "Laptop", "SKU-1", true);
        when(warehouseService.getProductWarehouse(warehouse.getId(), "Lap", "SKU", FilterExistInWhSelection.YES))
            .thenReturn(List.of(productInfoVm));

        Stock stock = Stock.builder()
            .id(20L)
            .productId(productId)
            .quantity(3L)
            .reservedQuantity(1L)
            .warehouse(warehouse)
            .build();
        when(stockRepository.findByWarehouseIdAndProductIdIn(warehouse.getId(), List.of(productId)))
            .thenReturn(List.of(stock));

        List<StockVm> result = stockService.getStocksByWarehouseIdAndProductNameAndSku(
            warehouse.getId(), "Lap", "SKU");

        assertThat(result).hasSize(1);
        StockVm stockVm = result.getFirst();
        assertThat(stockVm.productId()).isEqualTo(productId);
        assertThat(stockVm.productName()).isEqualTo("Laptop");
        assertThat(stockVm.warehouseId()).isEqualTo(warehouse.getId());
    }

    @Test
    void updateProductQuantityInStock_whenAdjustedGreaterThanCurrent_shouldThrowBadRequest() {
        StockQuantityVm quantityVm = new StockQuantityVm(1L, -5L, "note");
        Stock stock = Stock.builder().id(1L).productId(9L).quantity(-10L).warehouse(warehouse).build();
        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));

        assertThrows(BadRequestException.class,
            () -> stockService.updateProductQuantityInStock(new StockQuantityUpdateVm(List.of(quantityVm))));

        verify(stockRepository, never()).saveAll(any());
        verify(stockHistoryService, never()).createStockHistories(any(), any());
    }

    @Test
    void updateProductQuantityInStock_whenValid_shouldUpdateAndPropagateQuantity() {
        StockQuantityVm updateStock1 = new StockQuantityVm(1L, -2L, "decrease");
        StockQuantityVm updateStock2 = new StockQuantityVm(2L, null, null);

        Stock stock1 = Stock.builder()
            .id(1L)
            .productId(11L)
            .quantity(10L)
            .reservedQuantity(0L)
            .warehouse(warehouse)
            .build();
        Stock stock2 = Stock.builder()
            .id(2L)
            .productId(12L)
            .quantity(5L)
            .reservedQuantity(0L)
            .warehouse(warehouse)
            .build();

        when(stockRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(stock1, stock2));

        stockService.updateProductQuantityInStock(new StockQuantityUpdateVm(List.of(updateStock1, updateStock2)));

        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).saveAll(captor.capture());
        List<Stock> savedStocks = captor.getValue();
        assertThat(savedStocks.get(0).getQuantity()).isEqualTo(8L);
        assertThat(savedStocks.get(1).getQuantity()).isEqualTo(5L);

        verify(stockHistoryService).createStockHistories(savedStocks, List.of(updateStock1, updateStock2));

        ArgumentCaptor<List<ProductQuantityPostVm>> productCaptor = ArgumentCaptor.forClass(List.class);
        verify(productService).updateProductQuantity(productCaptor.capture());
        List<ProductQuantityPostVm> quantityPosts = productCaptor.getValue();
        assertThat(quantityPosts)
            .extracting(ProductQuantityPostVm::stockQuantity)
            .containsExactly(8L, 5L);
    }
}