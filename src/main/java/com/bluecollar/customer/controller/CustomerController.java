package com.bluecollar.customer.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import com.bluecollar.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/api/v1/customers/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(customerService.getMyProfile(), "Customer profile fetched successfully"));
    }

    @PutMapping("/api/v1/customers/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateMyProfile(
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerService.updateMyProfile(request), "Customer profile updated successfully"));
    }

    @GetMapping("/api/v1/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAllCustomers(pageable), "Customers fetched successfully"));
    }

    @GetMapping("/api/v1/customers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getCustomerById(id), "Customer fetched successfully"));
    }

    @PutMapping("/api/v1/customers/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCustomer(@PathVariable UUID id) {
        customerService.deactivateCustomer(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Customer deactivated successfully"));
    }
}
