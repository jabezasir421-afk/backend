package com.bluecollar.auth.service;

import com.bluecollar.auth.dto.*;

public interface AuthService {

    AuthResponse registerCustomer(RegisterCustomerRequest request);

    AuthResponse registerWorker(RegisterWorkerRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    CurrentUserResponse getCurrentUser();

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(ChangePasswordRequest request);

    void verifyEmail(VerifyEmailRequest request);

    void resendVerificationEmail(ResendVerificationEmailRequest request);
}
