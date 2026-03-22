package com.yas.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.DuplicatedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.model.enumeration.FilterExistInWhSelection;
import com.yas.inventory.repository.StockRepository;
import com.yas.inventory.repository.WarehouseRepository;
import com.yas.inventory.viewmodel.address.AddressDetailVm;
import com.yas.inventory.viewmodel.address.AddressPostVm;
import com.yas.inventory.viewmodel.address.AddressVm;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.warehouse.WarehouseDetailVm;
import com.yas.inventory.viewmodel.warehouse.WarehouseListGetVm;
import com.yas.inventory.viewmodel.warehouse.WarehousePostVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductService productService;
    @Mock
    private LocationService locationService;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse warehouse;
    private WarehousePostVm warehousePostVm;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(1L).name("Central").addressId(100L).build();
        warehousePostVm = WarehousePostVm.builder()
            .name("Central")
            .contactName("John")
            .phone("123")
            .addressLine1("line1")
            .addressLine2("line2")
            .city("city")
            .zipCode("zip")
            .districtId(1L)
            .stateOrProvinceId(2L)
            .countryId(3L)
            .build();
    }

    @Test
    void getProductWarehouse_whenProductsExistInWarehouse_shouldMarkExistFlag() {
        when(stockRepository.getProductIdsInWarehouse(warehouse.getId())).thenReturn(List.of(5L));
        when(productService.filterProducts("name", "sku", List.of(5L), FilterExistInWhSelection.YES))
            .thenReturn(List.of(new ProductInfoVm(5L, "Phone", "S", true)));

        List<ProductInfoVm> result = warehouseService.getProductWarehouse(
            warehouse.getId(), "name", "sku", FilterExistInWhSelection.YES);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().existInWh()).isTrue();
    }

    @Test
    void getProductWarehouse_whenWarehouseHasNoStock_shouldReturnFilteredList() {
        when(stockRepository.getProductIdsInWarehouse(warehouse.getId())).thenReturn(List.of());
        when(productService.filterProducts("n", "s", List.of(), FilterExistInWhSelection.NO))
            .thenReturn(List.of(new ProductInfoVm(2L, "Book", "B", false)));

        List<ProductInfoVm> result = warehouseService.getProductWarehouse(
            warehouse.getId(), "n", "s", FilterExistInWhSelection.NO);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().existInWh()).isFalse();
    }

    @Test
    void findById_whenMissing_shouldThrowNotFound() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> warehouseService.findById(warehouse.getId()));
    }

    @Test
    void findById_whenFound_shouldReturnDetail() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        AddressDetailVm addressDetailVm = AddressDetailVm.builder()
            .id(warehouse.getAddressId())
            .contactName("John")
            .phone("123")
            .addressLine1("line1")
            .addressLine2("line2")
            .city("city")
            .zipCode("zip")
            .districtId(10L)
            .stateOrProvinceId(11L)
            .countryId(12L)
            .build();
        when(locationService.getAddressById(warehouse.getAddressId())).thenReturn(addressDetailVm);

        WarehouseDetailVm result = warehouseService.findById(warehouse.getId());

        assertThat(result.id()).isEqualTo(warehouse.getId());
        assertThat(result.contactName()).isEqualTo("John");
    }

    @Test
    void create_whenNameAlreadyExists_shouldThrowDuplicate() {
        when(warehouseRepository.existsByName(warehousePostVm.name())).thenReturn(true);

        assertThrows(DuplicatedException.class, () -> warehouseService.create(warehousePostVm));
        verify(locationService, never()).createAddress(any());
    }

    @Test
    void create_whenValid_shouldPersistWarehouse() {
        when(warehouseRepository.existsByName(warehousePostVm.name())).thenReturn(false);
        when(locationService.createAddress(any(AddressPostVm.class)))
            .thenReturn(AddressVm.builder().id(50L).build());
        when(warehouseRepository.save(any(Warehouse.class)))
            .thenAnswer(invocation -> {
                Warehouse wh = invocation.getArgument(0);
                wh.setId(2L);
                return wh;
            });

        Warehouse saved = warehouseService.create(warehousePostVm);

        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Central");
        assertThat(saved.getAddressId()).isEqualTo(50L);
    }

    @Test
    void update_whenWarehouseMissing_shouldThrowNotFound() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> warehouseService.update(warehousePostVm, warehouse.getId()));
    }

    @Test
    void update_whenNameDuplicated_shouldThrow() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameWithDifferentId(warehousePostVm.name(), warehouse.getId()))
            .thenReturn(true);

        assertThrows(DuplicatedException.class, () -> warehouseService.update(warehousePostVm, warehouse.getId()));
        verify(locationService, never()).updateAddress(any(), any());
    }

    @Test
    void update_whenValid_shouldUpdateWarehouseAndAddress() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameWithDifferentId(warehousePostVm.name(), warehouse.getId()))
            .thenReturn(false);

        warehouseService.update(warehousePostVm, warehouse.getId());

        verify(locationService).updateAddress(warehouse.getAddressId(), new AddressPostVm(
            warehousePostVm.contactName(),
            warehousePostVm.phone(),
            warehousePostVm.addressLine1(),
            warehousePostVm.addressLine2(),
            warehousePostVm.city(),
            warehousePostVm.zipCode(),
            warehousePostVm.districtId(),
            warehousePostVm.stateOrProvinceId(),
            warehousePostVm.countryId()
        ));
        verify(warehouseRepository).save(warehouse);
    }

    @Test
    void delete_whenWarehouseMissing_shouldThrow() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> warehouseService.delete(warehouse.getId()));
    }

    @Test
    void delete_whenFound_shouldRemoveWarehouseAndAddress() {
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        warehouseService.delete(warehouse.getId());

        verify(warehouseRepository).deleteById(warehouse.getId());
        verify(locationService).deleteAddress(warehouse.getAddressId());
    }

    @Test
    void getPageableWarehouses_shouldReturnPagedResult() {
        when(warehouseRepository.findAll(PageRequest.of(0, 2)))
            .thenReturn(new PageImpl<>(List.of(warehouse), PageRequest.of(0, 2), 1));

        WarehouseListGetVm vm = warehouseService.getPageableWarehouses(0, 2);

        assertThat(vm.warehouseContent()).hasSize(1);
        assertThat(vm.totalElements()).isEqualTo(1);
        assertThat(vm.totalPages()).isEqualTo(1);
        assertThat(vm.isLast()).isTrue();
    }
}