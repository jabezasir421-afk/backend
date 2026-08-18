package com.bluecollar.customer.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import com.bluecollar.customer.dto.UpdateProfilePhotoRequest;
import com.bluecollar.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Customers", description = "Customer profile management")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/api/v1/customers/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get my customer profile",
            description = "Retrieve the current authenticated customer's profile information",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(customerService.getMyProfile(), "Customer profile fetched successfully"));
    }

    @PutMapping("/api/v1/customers/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Update my customer profile",
            description = "Update customer name and basic information (excludes profile photo)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> updateMyProfile(
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerService.updateMyProfile(request), "Customer profile updated successfully"));
    }

    @PutMapping("/api/v1/customers/me/profile-photo")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Update my profile photo",
            description = "Set or update profile photo by providing the file ID of an uploaded PROFILE_PHOTO file",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> updateMyProfilePhoto(
            @Valid @RequestBody UpdateProfilePhotoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerService.updateMyProfilePhoto(request), "Profile photo updated successfully"));
    }

    @GetMapping("/api/v1/customers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all customers",
            description = "Admin endpoint to retrieve all customers",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAllCustomers(pageable), "Customers fetched successfully"));
    }

    @GetMapping("/api/v1/customers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get customer by ID",
            description = "Admin endpoint to retrieve a specific customer's profile",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getCustomerById(id), "Customer fetched successfully"));
    }

    @PutMapping("/api/v1/customers/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Deactivate a customer",
            description = "Admin endpoint to deactivate a customer account (soft-delete)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deactivateCustomer(@PathVariable UUID id) {
        customerService.deactivateCustomer(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Customer deactivated successfully"));
    }
}
