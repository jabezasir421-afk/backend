package com.bluecollar.customer.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.exception.CustomerNotFoundException;
import com.bluecollar.customer.mapper.CustomerMapper;
import com.bluecollar.customer.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer customer;
    private UserAccount userAccount;
    private CustomerResponse response;
    private UpdateCustomerRequest updateRequest;
    private UUID userAccountId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        userAccountId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        userAccount = new UserAccount();
        userAccount.setId(userAccountId);
        userAccount.setEmail("john.doe@example.com");
        userAccount.setPhoneNumber("+919876543210");
        userAccount.setRole(UserRole.CUSTOMER);
        userAccount.setActive(true);

        customer = new Customer();
        customer.setId(customerId);
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setProfilePhotoFileId(null);
        customer.setActive(true);
        customer.setUserAccount(userAccount);

        updateRequest = new UpdateCustomerRequest("Jane", "Smith");

        response = new CustomerResponse(
                customerId, userAccountId,
                "John", "Doe",
                "john.doe@example.com", "+919876543210",
                null, true, null, null
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userAccountId, "john.doe@example.com", UserRole.CUSTOMER);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── getMyProfile ────────────────────────────────────────────────────────

    @Test
    void getMyProfileShouldReturnCustomerResponseForCurrentUser() {
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = customerService.getMyProfile();

        assertEquals(response, result);
        verify(customerRepository).findByUserAccountId(userAccountId);
        verify(customerMapper).toResponse(customer);
    }

    @Test
    void getMyProfileShouldThrowWhenCustomerProfileNotFoundForCurrentUser() {
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.getMyProfile());
        verify(customerRepository).findByUserAccountId(userAccountId);
        verify(customerMapper, never()).toResponse(any());
    }

    // ─── updateMyProfile ─────────────────────────────────────────────────────

    @Test
    void updateMyProfileShouldUpdateAndReturnUpdatedResponse() {
        UUID photoFileId = UUID.randomUUID();
        Customer savedCustomer = new Customer();
        savedCustomer.setId(customerId);
        savedCustomer.setFirstName("Jane");
        savedCustomer.setLastName("Smith");
        savedCustomer.setProfilePhotoFileId(photoFileId);
        savedCustomer.setActive(true);
        savedCustomer.setUserAccount(userAccount);

        CustomerResponse updatedResponse = new CustomerResponse(
                customerId, userAccountId,
                "Jane", "Smith",
                "john.doe@example.com", "+919876543210",
                photoFileId, true, null, null
        );

        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(savedCustomer);
        when(customerMapper.toResponse(savedCustomer)).thenReturn(updatedResponse);

        CustomerResponse result = customerService.updateMyProfile(updateRequest);

        assertEquals(updatedResponse, result);
        verify(customerRepository).findByUserAccountId(userAccountId);
        verify(customerMapper).updateEntity(customer, updateRequest);
        verify(customerRepository).save(customer);
        verify(customerMapper).toResponse(savedCustomer);
    }

    @Test
    void updateMyProfileShouldThrowWhenCustomerProfileNotFoundForCurrentUser() {
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.updateMyProfile(updateRequest));
        verify(customerRepository).findByUserAccountId(userAccountId);
        verify(customerRepository, never()).save(any());
    }

    // ─── getCustomerById ─────────────────────────────────────────────────────

    @Test
    void getCustomerByIdShouldReturnCustomerResponseWhenCustomerExists() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = customerService.getCustomerById(customerId);

        assertEquals(response, result);
        verify(customerRepository).findById(customerId);
        verify(customerMapper).toResponse(customer);
    }

    @Test
    void getCustomerByIdShouldThrowWhenCustomerDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(customerRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomerById(missingId));
        verify(customerRepository).findById(missingId);
        verify(customerMapper, never()).toResponse(any());
    }

    // ─── getAllCustomers ──────────────────────────────────────────────────────

    @Test
    void getAllCustomersShouldReturnPageOfCustomerResponses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(customer), pageable, 1);

        when(customerRepository.findAll(pageable)).thenReturn(page);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        Page<CustomerResponse> result = customerService.getAllCustomers(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().getFirst());
        verify(customerRepository).findAll(pageable);
    }

    @Test
    void getAllCustomersShouldReturnEmptyPageWhenNoCustomersExist() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Customer> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(customerRepository.findAll(pageable)).thenReturn(emptyPage);

        Page<CustomerResponse> result = customerService.getAllCustomers(pageable);

        assertEquals(0, result.getTotalElements());
        assertEquals(List.of(), result.getContent());
        verify(customerRepository).findAll(pageable);
    }

    // ─── deactivateCustomer ──────────────────────────────────────────────────

    @Test
    void deactivateCustomerShouldSetCustomerAndUserAccountInactive() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);
        when(userAccountRepository.save(userAccount)).thenReturn(userAccount);

        customerService.deactivateCustomer(customerId);

        assertEquals(false, customer.getActive());
        assertEquals(false, userAccount.getActive());
        verify(customerRepository).findById(customerId);
        verify(customerRepository).save(customer);
        verify(userAccountRepository).save(userAccount);
    }

    @Test
    void deactivateCustomerShouldThrowWhenCustomerDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(customerRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.deactivateCustomer(missingId));
        verify(customerRepository).findById(missingId);
        verify(customerRepository, never()).save(any());
        verify(userAccountRepository, never()).save(any());
    }
}
