package com.bluecollar.auth.dto;

import com.bluecollar.auth.entity.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserRole role,
        UUID profileId
) {
}
