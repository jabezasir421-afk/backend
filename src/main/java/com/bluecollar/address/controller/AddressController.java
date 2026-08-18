package com.bluecollar.address.controller;

import com.bluecollar.address.dto.AddressResponse;
import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.dto.UpdateAddressRequest;
import com.bluecollar.address.service.AddressService;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Addresses", description = "Customer address management")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @Operation(summary = "Create a new address")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @Valid @RequestBody CreateAddressRequest request
    ) {
        AddressResponse address = addressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(address, "Address created successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all active addresses", description = "Retrieve all active addresses for the authenticated customer")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses() {
        return ResponseEntity.ok(ApiResponse.success(addressService.getMyAddresses(), "Addresses fetched successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get address by ID")
    public ResponseEntity<ApiResponse<AddressResponse>> getMyAddressById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(addressService.getMyAddressById(id), "Address fetched successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an address", description = "Update address details. Only active addresses can be updated.")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAddressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(addressService.updateAddress(id, request), "Address updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deactivate an address",
            description = "Soft-delete (deactivate) an address. The address remains in the system for booking history but is no longer available for new bookings. " +
                    "If the deleted address is marked as default, the default status is cleared."
    )
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deactivated successfully"));
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Set default address", description = "Mark this address as the default for new bookings. Clears the default status from any previously default address.")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(addressService.setDefaultAddress(id), "Default address updated successfully"));
    }
}
