package com.bluecollar.admin.dto;

import com.bluecollar.auth.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String phoneNumber,
        UserRole role,
        boolean active,
        boolean emailVerified,
        Instant lastLoginAt,
        UUID profileId,
        String profileType
) {
}
