package com.yas.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.inventory.model.Stock;
import com.yas.inventory.model.StockHistory;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.repository.StockHistoryRepository;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.stock.StockQuantityVm;
import com.yas.inventory.viewmodel.stockhistory.StockHistoryListVm;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockHistoryServiceTest {

    @Mock
    private StockHistoryRepository stockHistoryRepository;
    @Mock
    private ProductService productService;

    @InjectMocks
    private StockHistoryService stockHistoryService;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(9L).name("WH").addressId(99L).build();
    }

    @Test
    void createStockHistories_whenNoMatchingQuantity_shouldPersistNothing() {
        Stock stock = Stock.builder().id(1L).productId(3L).warehouse(warehouse).build();

        stockHistoryService.createStockHistories(List.of(stock), List.of());

        ArgumentCaptor<List<StockHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void createStockHistories_whenMatchingQuantity_shouldPersistHistory() {
        Stock stock = Stock.builder().id(2L).productId(5L).warehouse(warehouse).build();
        StockQuantityVm stockQuantityVm = new StockQuantityVm(2L, 7L, "restock");

        stockHistoryService.createStockHistories(List.of(stock), List.of(stockQuantityVm));

        ArgumentCaptor<List<StockHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockHistoryRepository).saveAll(captor.capture());
        List<StockHistory> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        StockHistory history = saved.getFirst();
        assertThat(history.getProductId()).isEqualTo(5L);
        assertThat(history.getAdjustedQuantity()).isEqualTo(7L);
        assertThat(history.getNote()).isEqualTo("restock");
        assertThat(history.getWarehouse()).isSameAs(warehouse);
    }

    @Test
    void getStockHistories_shouldReturnViewModelsWithProductName() {
        StockHistory stockHistory = StockHistory.builder()
            .id(10L)
            .productId(8L)
            .warehouse(warehouse)
            .adjustedQuantity(4L)
            .note("note")
            .build();
        stockHistory.setCreatedBy("tester");
        stockHistory.setCreatedOn(ZonedDateTime.now());

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(8L, warehouse.getId()))
            .thenReturn(List.of(stockHistory));
        when(productService.getProduct(8L)).thenReturn(new ProductInfoVm(8L, "Phone", "SKU-P", true));

        StockHistoryListVm result = stockHistoryService.getStockHistories(8L, warehouse.getId());

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().productName()).isEqualTo("Phone");
        assertThat(result.data().getFirst().adjustedQuantity()).isEqualTo(4L);
    }
}