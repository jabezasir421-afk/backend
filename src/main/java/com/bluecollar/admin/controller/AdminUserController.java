package com.bluecollar.admin.controller;

import com.bluecollar.admin.dto.AdminUserResponse;
import com.bluecollar.admin.service.AdminUserService;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.dto.ApiResponse;
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
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
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
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUser(id), "User fetched successfully"));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> activateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.activateUser(id), "User activated"));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.deactivateUser(id), "User deactivated"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @PathVariable UUID id,
            @RequestParam UserRole role
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.changeRole(id, role), "Role updated"));
    }
}
