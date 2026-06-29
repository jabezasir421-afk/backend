package com.bluecollar.address.service;

import com.bluecollar.address.dto.AddressResponse;
import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.dto.UpdateAddressRequest;
import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.exception.AddressNotFoundException;
import com.bluecollar.address.exception.MaxAddressesExceededException;
import com.bluecollar.address.mapper.AddressMapper;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.service.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CustomerServiceImpl customerService;

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    private Customer customer;
    private Address address;
    private AddressResponse addressResponse;
    private CreateAddressRequest createRequest;
    private UpdateAddressRequest updateRequest;
    private UUID customerId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        addressId = UUID.randomUUID();

        customer = new Customer();
        customer.setId(customerId);
        customer.setFirstName("John");
        customer.setLastName("Doe");

        address = new Address();
        address.setId(addressId);
        address.setCustomer(customer);
        address.setLabel("Home");
        address.setAddressType(AddressType.HOME);
        address.setLine1("123 Main St");
        address.setLine2("Apt 4B");
        address.setLandmark("Near Park");
        address.setCity("Mumbai");
        address.setState("Maharashtra");
        address.setPincode("400001");
        address.setLatitude(new BigDecimal("19.0760"));
        address.setLongitude(new BigDecimal("72.8777"));
        address.setIsDefault(true);
        address.setActive(true);

        createRequest = new CreateAddressRequest(
                "Home",
                AddressType.HOME,
                "123 Main St",
                "Apt 4B",
                "Near Park",
                "Mumbai",
                "Maharashtra",
                "400001",
                new BigDecimal("19.0760"),
                new BigDecimal("72.8777"),
                true
        );

        updateRequest = new UpdateAddressRequest(
                "Work",
                AddressType.WORK,
                "456 Office Rd",
                "Suite 100",
                "Tech Park",
                "Mumbai",
                "Maharashtra",
                "400002",
                new BigDecimal("19.0800"),
                new BigDecimal("72.8800"),
                true
        );

        addressResponse = new AddressResponse(
                addressId,
                customerId,
                "Home",
                AddressType.HOME,
                "123 Main St",
                "Apt 4B",
                "Near Park",
                "Mumbai",
                "Maharashtra",
                "400001",
                new BigDecimal("19.0760"),
                new BigDecimal("72.8777"),
                true,
                true,
                Instant.now(),
                Instant.now()
        );
    }

    // ─── CREATE ADDRESS ──────────────────────────────────────────────────────

    @Test
    void createAddressShouldCreateNewDefaultAddressWhenFirstAddress() {
        // Arrange
        CreateAddressRequest nonDefaultRequest = new CreateAddressRequest(
                "Home", AddressType.HOME, "123 Main St", "Apt 4B", "Near Park",
                "Mumbai", "Maharashtra", "400001", new BigDecimal("19.0760"),
                new BigDecimal("72.8777"), false
        );
        address.setIsDefault(false); // starts false, but should become default

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.countByCustomerIdAndActiveTrue(customerId)).thenReturn(0L);
        when(addressMapper.toEntity(nonDefaultRequest, customer)).thenReturn(address);
        when(addressRepository.save(address)).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        AddressResponse result = addressService.createAddress(nonDefaultRequest);

        // Assert
        assertTrue(address.getIsDefault()); // Verified that it shouldBeDefault
        assertEquals(addressResponse, result);
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository, times(2)).countByCustomerIdAndActiveTrue(customerId);
        verify(addressMapper).toEntity(nonDefaultRequest, customer);
        verify(addressRepository).save(address);
        verify(addressMapper).toResponse(address);
    }

    @Test
    void createAddressShouldClearOldDefaultsWhenNewDefaultAddressCreated() {
        // Arrange
        Address existingDefault = new Address();
        existingDefault.setId(UUID.randomUUID());
        existingDefault.setCustomer(customer);
        existingDefault.setIsDefault(true);
        existingDefault.setActive(true);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.countByCustomerIdAndActiveTrue(customerId)).thenReturn(1L);
        when(addressMapper.toEntity(createRequest, customer)).thenReturn(address);
        when(addressRepository.findByCustomerIdAndIsDefaultTrueAndActiveTrue(customerId))
                .thenReturn(List.of(existingDefault));
        when(addressRepository.save(address)).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        AddressResponse result = addressService.createAddress(createRequest);

        // Assert
        assertFalse(existingDefault.getIsDefault());
        assertTrue(address.getIsDefault());
        assertEquals(addressResponse, result);
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).countByCustomerIdAndActiveTrue(customerId);
        verify(addressMapper).toEntity(createRequest, customer);
        verify(addressRepository).findByCustomerIdAndIsDefaultTrueAndActiveTrue(customerId);
        verify(addressRepository).save(address);
        verify(addressMapper).toResponse(address);
    }

    @Test
    void createAddressShouldThrowMaxAddressesExceededExceptionWhenLimitReached() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.countByCustomerIdAndActiveTrue(customerId)).thenReturn(10L);

        // Act & Assert
        assertThrows(MaxAddressesExceededException.class, () -> addressService.createAddress(createRequest));
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).countByCustomerIdAndActiveTrue(customerId);
        verify(addressMapper, never()).toEntity(any(), any());
        verify(addressRepository, never()).save(any());
    }

    // ─── GET MY ADDRESSES ────────────────────────────────────────────────────

    @Test
    void getMyAddressesShouldReturnOrderedAddressesForCurrentUser() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(customerId))
                .thenReturn(List.of(address));
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        List<AddressResponse> result = addressService.getMyAddresses();

        // Assert
        assertEquals(1, result.size());
        assertEquals(addressResponse, result.get(0));
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(customerId);
        verify(addressMapper).toResponse(address);
    }

    // ─── GET MY ADDRESS BY ID ────────────────────────────────────────────────

    @Test
    void getMyAddressByIdShouldReturnAddressWhenOwnedByCurrentUser() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        AddressResponse result = addressService.getMyAddressById(addressId);

        // Assert
        assertEquals(addressResponse, result);
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
        verify(addressMapper).toResponse(address);
    }

    @Test
    void getMyAddressByIdShouldThrowAddressNotFoundExceptionWhenNotOwnedOrNotFound() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AddressNotFoundException.class, () -> addressService.getMyAddressById(addressId));
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
        verify(addressMapper, never()).toResponse(any());
    }

    // ─── UPDATE ADDRESS ──────────────────────────────────────────────────────

    @Test
    void updateAddressShouldUpdateAndReturnResponseWhenOwnedByCurrentUser() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(addressRepository.save(address)).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        AddressResponse result = addressService.updateAddress(addressId, updateRequest);

        // Assert
        assertEquals(addressResponse, result);
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
        verify(addressMapper).updateEntity(address, updateRequest);
        verify(addressRepository).save(address);
        verify(addressMapper).toResponse(address);
    }

    // ─── DELETE ADDRESS ──────────────────────────────────────────────────────

    @Test
    void deleteAddressShouldDeactivateAndDeleteDefaultFlag() {
        // Arrange
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(addressRepository.save(address)).thenReturn(address);

        // Act
        addressService.deleteAddress(addressId);

        // Assert
        assertFalse(address.getActive());
        assertFalse(address.getIsDefault());
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
        verify(addressRepository).save(address);
    }

    // ─── SET DEFAULT ADDRESS ─────────────────────────────────────────────────

    @Test
    void setDefaultAddressShouldClearOldDefaultsAndSetNewDefault() {
        // Arrange
        Address existingDefault = new Address();
        existingDefault.setId(UUID.randomUUID());
        existingDefault.setCustomer(customer);
        existingDefault.setIsDefault(true);
        existingDefault.setActive(true);

        address.setIsDefault(false); // starts false

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(addressRepository.findByCustomerIdAndIsDefaultTrueAndActiveTrue(customerId))
                .thenReturn(List.of(existingDefault));
        when(addressRepository.save(address)).thenReturn(address);
        when(addressMapper.toResponse(address)).thenReturn(addressResponse);

        // Act
        AddressResponse result = addressService.setDefaultAddress(addressId);

        // Assert
        assertFalse(existingDefault.getIsDefault());
        assertTrue(address.getIsDefault());
        assertTrue(address.getActive());
        assertEquals(addressResponse, result);
        verify(customerService).findCustomerByCurrentUser();
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
        verify(addressRepository).findByCustomerIdAndIsDefaultTrueAndActiveTrue(customerId);
        verify(addressRepository).save(address);
        verify(addressMapper).toResponse(address);
    }

    // ─── FIND ACTIVE ADDRESS FOR CUSTOMER ────────────────────────────────────

    @Test
    void findActiveAddressForCustomerShouldReturnAddressWhenActive() {
        // Arrange
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));

        // Act
        Address result = addressService.findActiveAddressForCustomer(addressId, customerId);

        // Assert
        assertEquals(address, result);
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
    }

    @Test
    void findActiveAddressForCustomerShouldThrowAddressNotFoundExceptionWhenInactive() {
        // Arrange
        address.setActive(false);
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));

        // Act & Assert
        assertThrows(AddressNotFoundException.class, () -> addressService.findActiveAddressForCustomer(addressId, customerId));
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
    }

    @Test
    void findActiveAddressForCustomerShouldThrowAddressNotFoundExceptionWhenNotFound() {
        // Arrange
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AddressNotFoundException.class, () -> addressService.findActiveAddressForCustomer(addressId, customerId));
        verify(addressRepository).findByIdAndCustomerId(addressId, customerId);
    }
}
