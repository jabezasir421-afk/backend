package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.AdminUserResponse;
import com.bluecollar.auth.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {

    Page<AdminUserResponse> listUsers(UserRole role, Boolean active, String email, Pageable pageable);

    AdminUserResponse getUser(UUID id);

    AdminUserResponse activateUser(UUID id);

    AdminUserResponse deactivateUser(UUID id);

    AdminUserResponse changeRole(UUID id, UserRole role);
}
