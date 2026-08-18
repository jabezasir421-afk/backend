package com.bluecollar.address.dto;

import com.bluecollar.address.entity.AddressType;
import com.bluecollar.common.validation.IndianPincode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateAddressRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 50, message = "Label must not exceed 50 characters")
        String label,

        @NotNull(message = "Address type is required")
        AddressType addressType,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
        String line1,

        @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
        String line2,

        @Size(max = 255, message = "Landmark must not exceed 255 characters")
        String landmark,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "Pincode is required")
        @IndianPincode
        String pincode,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal longitude,

        @NotNull(message = "Active status is required")
        Boolean active
) {
}
