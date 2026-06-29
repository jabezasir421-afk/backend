package com.bluecollar.customer.service;

import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse getMyProfile();

    CustomerResponse updateMyProfile(UpdateCustomerRequest request);

    CustomerResponse getCustomerById(UUID id);

    Page<CustomerResponse> getAllCustomers(Pageable pageable);

    void deactivateCustomer(UUID id);
}
