package com.bluecollar.admin.controller;

import com.bluecollar.admin.dto.AdminUserResponse;
import com.bluecollar.admin.service.AdminUserService;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "Admin user management and activation control")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(
            summary = "List all users",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve all users with optional filtering by role, active status, or email.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String email,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.listUsers(role, active, email, pageable),
                "Users fetched successfully"
        ));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get user by ID",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve details for a specific user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUser(id), "User fetched successfully"));
    }

    @PutMapping("/{id}/activate")
    @Operation(
            summary = "Activate user",
            description = "Admin endpoint requiring JWT token with ADMIN role. Activate a deactivated user account.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminUserResponse>> activateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.activateUser(id), "User activated"));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate user",
            description = "Admin endpoint requiring JWT token with ADMIN role. Deactivate an active user account.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminUserResponse>> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.deactivateUser(id), "User deactivated"));
    }

    @PutMapping("/{id}/role")
    @Operation(
            summary = "Change user role",
            description = "Admin endpoint requiring JWT token with ADMIN role. Change a user's role (ADMIN, CUSTOMER, or WORKER).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @PathVariable UUID id,
            @RequestParam UserRole role
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.changeRole(id, role), "Role updated"));
    }
}
