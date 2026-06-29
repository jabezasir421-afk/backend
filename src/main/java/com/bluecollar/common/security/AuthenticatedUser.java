package com.bluecollar.common.security;

import com.bluecollar.auth.entity.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userAccountId,
        String email,
        UserRole role
) {
}
