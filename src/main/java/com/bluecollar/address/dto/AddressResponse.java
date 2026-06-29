package com.bluecollar.address.dto;

import com.bluecollar.address.entity.AddressType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID customerId,
        String label,
        AddressType addressType,
        String line1,
        String line2,
        String landmark,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefault,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
