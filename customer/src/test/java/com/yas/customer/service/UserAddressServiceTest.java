package com.yas.customer.service;

import static com.yas.customer.util.SecurityContextUtils.setUpSecurityContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.AccessDeniedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.customer.model.UserAddress;
import com.yas.customer.repository.UserAddressRepository;
import com.yas.customer.viewmodel.address.ActiveAddressVm;
import com.yas.customer.viewmodel.address.AddressDetailVm;
import com.yas.customer.viewmodel.address.AddressPostVm;
import com.yas.customer.viewmodel.address.AddressVm;
import com.yas.customer.viewmodel.useraddress.UserAddressVm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private UserAddressService userAddressService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserAddressList_whenAnonymousUser_throwAccessDeniedException() {
        setUpSecurityContext("anonymousUser");

        assertThrows(AccessDeniedException.class, () -> userAddressService.getUserAddressList());
    }

    @Test
    void getUserAddressList_whenAddressesReturned_sortedWithActiveFirst() {
        setUpSecurityContext("user-1");

        List<UserAddress> userAddresses = List.of(
            UserAddress.builder().userId("user-1").addressId(1L).isActive(false).build(),
            UserAddress.builder().userId("user-1").addressId(2L).isActive(true).build()
        );
        when(userAddressRepository.findAllByUserId("user-1")).thenReturn(userAddresses);

        List<AddressDetailVm> addressDetails = List.of(
            buildAddressDetailVm(1L, "Address One"),
            buildAddressDetailVm(2L, "Address Two")
        );
        when(locationService.getAddressesByIdList(List.of(1L, 2L))).thenReturn(addressDetails);

        List<ActiveAddressVm> result = userAddressService.getUserAddressList();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().isActive()).isTrue();
        assertThat(result.getFirst().id()).isEqualTo(2L);
    }

    @Test
    void getAddressDefault_whenAddressExists_returnAddressDetailVm() {
        setUpSecurityContext("user-1");
        UserAddress activeAddress = UserAddress.builder().addressId(5L).userId("user-1").isActive(true).build();
        when(userAddressRepository.findByUserIdAndIsActiveTrue("user-1")).thenReturn(java.util.Optional.of(activeAddress));
        AddressDetailVm expected = buildAddressDetailVm(5L, "Default");
        when(locationService.getAddressById(5L)).thenReturn(expected);

        AddressDetailVm result = userAddressService.getAddressDefault();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getAddressDefault_whenActiveAddressMissing_throwNotFoundException() {
        setUpSecurityContext("user-1");
        when(userAddressRepository.findByUserIdAndIsActiveTrue("user-1")).thenReturn(java.util.Optional.empty());

        assertThrows(NotFoundException.class, () -> userAddressService.getAddressDefault());
    }

    @Test
    void createAddress_whenFirstAddress_marksAsActive() {
        setUpSecurityContext("user-1");
        when(userAddressRepository.findAllByUserId("user-1")).thenReturn(Collections.emptyList());

        AddressPostVm request = new AddressPostVm("John", "111", "Line", "City", "00000", 1L, 2L, 3L);
        AddressVm createdAddress = AddressVm.builder().id(10L).contactName("John").phone("111")
            .addressLine1("Line").city("City").zipCode("00000").districtId(1L).stateOrProvinceId(2L)
            .countryId(3L).build();
        when(locationService.createAddress(request)).thenReturn(createdAddress);

        UserAddress savedAddress = UserAddress.builder().id(100L).userId("user-1").addressId(10L).isActive(true).build();
        when(userAddressRepository.save(any(UserAddress.class))).thenReturn(savedAddress);

        UserAddressVm result = userAddressService.createAddress(request);

        assertThat(result.isActive()).isTrue();
        assertThat(result.addressGetVm().id()).isEqualTo(10L);
        assertThat(result.userId()).isEqualTo("user-1");
    }

    @Test
    void deleteAddress_whenAddressMissing_throwNotFoundException() {
        setUpSecurityContext("user-1");
        when(userAddressRepository.findOneByUserIdAndAddressId("user-1", 1L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> userAddressService.deleteAddress(1L));
    }

    @Test
    void chooseDefaultAddress_updatesActiveFlag() {
        setUpSecurityContext("user-1");
        List<UserAddress> userAddresses = new ArrayList<>();
        userAddresses.add(UserAddress.builder().userId("user-1").addressId(1L).isActive(true).build());
        userAddresses.add(UserAddress.builder().userId("user-1").addressId(2L).isActive(false).build());
        when(userAddressRepository.findAllByUserId("user-1")).thenReturn(userAddresses);

        userAddressService.chooseDefaultAddress(2L);

        ArgumentCaptor<List<UserAddress>> captor = ArgumentCaptor.forClass(List.class);
        verify(userAddressRepository).saveAll(captor.capture());
        List<UserAddress> updated = captor.getValue();
        assertThat(updated).hasSize(2);
        assertThat(updated.stream().filter(UserAddress::getIsActive).map(UserAddress::getAddressId).findFirst().orElse(null))
            .isEqualTo(2L);
        assertThat(updated.stream().filter(ua -> !ua.getIsActive()).map(UserAddress::getAddressId).findFirst().orElse(null))
            .isEqualTo(1L);
    }

    private AddressDetailVm buildAddressDetailVm(Long id, String contactName) {
        return AddressDetailVm.builder()
            .id(id)
            .contactName(contactName)
            .phone("123")
            .addressLine1("Line")
            .city("City")
            .zipCode("00000")
            .districtId(1L)
            .districtName("District")
            .stateOrProvinceId(2L)
            .stateOrProvinceName("State")
            .countryId(3L)
            .countryName("Country")
            .build();
    }
}
