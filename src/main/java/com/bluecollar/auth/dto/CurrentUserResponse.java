package com.bluecollar.auth.dto;

import com.bluecollar.auth.entity.UserRole;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userAccountId,
        String email,
        String phoneNumber,
        UserRole role,
        UUID customerId,
        UUID workerId
) {
}
