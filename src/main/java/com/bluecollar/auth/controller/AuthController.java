package com.bluecollar.auth.controller;

import com.bluecollar.auth.dto.*;
import com.bluecollar.auth.service.AuthService;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints for login, registration, and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/customer")
    @Operation(
            summary = "Register a new customer",
            description = "Register a new customer account without requiring authentication",
            security = {}
    )
    public ResponseEntity<ApiResponse<AuthResponse>> registerCustomer(
            @Valid @RequestBody RegisterCustomerRequest request
    ) {
        AuthResponse response = authService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Customer registered successfully"));
    }

    @PostMapping("/register/worker")
    @Operation(
            summary = "Register a new worker",
            description = "Register a new worker account without requiring authentication",
            security = {}
    )
    public ResponseEntity<ApiResponse<AuthResponse>> registerWorker(
            @Valid @RequestBody RegisterWorkerRequest request
    ) {
        AuthResponse response = authService.registerWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Worker registered successfully"));
    }

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate user with email and password to obtain JWT tokens",
            security = {}
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh authentication token",
            description = "Use refresh token to obtain a new JWT access token",
            security = {}
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request), "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Invalidate the current refresh token and logout the user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user information",
            description = "Retrieve the current authenticated user's profile information",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser(), "Current user fetched successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Initiate password reset",
            description = "Request a password reset. User will receive a reset token via email (simulated in this API). " +
                    "In production, this would send an email with a reset link.",
            security = {}
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "If an account with this email exists, a password reset token has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Complete password reset",
            description = "Reset password using the token sent via email. Requires the reset token and new password.",
            security = {}
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password (authenticated)",
            description = "Change password for the currently authenticated user. Requires verification of current password.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
