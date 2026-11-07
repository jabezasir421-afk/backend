package com.bluecollar.auth.service;

import com.bluecollar.auth.dto.*;
import com.bluecollar.auth.entity.RefreshToken;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.exception.InvalidCredentialsException;
import com.bluecollar.auth.exception.InvalidTokenException;
import com.bluecollar.auth.exception.UserAlreadyExistsException;
import com.bluecollar.auth.exception.UserNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import com.bluecollar.auth.repository.RefreshTokenRepository;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.security.JwtService;
import com.bluecollar.auth.security.TokenHashService;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.mapper.CustomerMapper;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.exception.SkillNotFoundException;
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerAlreadyExistsException;
import com.bluecollar.worker.repository.WorkerRepository;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import com.bluecollar.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final NotificationService notificationService;

    @Override
    public AuthResponse registerCustomer(RegisterCustomerRequest request) {
        validateUserUniqueness(request.email(), request.phoneNumber());

        UserAccount userAccount = userAccountRepository.save(UserAccount.builder()
                .email(normalizeEmail(request.email()))
                .phoneNumber(normalizePhone(request.phoneNumber()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)
                .build());

        Customer customer = customerRepository.save(
                customerMapper.toEntity(userAccount, request.firstName(), request.lastName())
        );

        sendVerificationEmail(userAccount);

        return buildAuthResponse(userAccount, customer.getId());
    }

    @Override
    public AuthResponse registerWorker(RegisterWorkerRequest request) {
        validateUserUniqueness(request.email(), request.phoneNumber());
        validateWorkerContactAvailable(request.phoneNumber(), request.email());

        Category category = findCategory(request.categoryId());
        Set<Skill> skills = findSkills(request.skillIds());

        UserAccount userAccount = userAccountRepository.save(UserAccount.builder()
                .email(normalizeEmail(request.email()))
                .phoneNumber(normalizePhone(request.phoneNumber()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.WORKER)
                .build());

        Worker worker = Worker.builder()
                .userAccount(userAccount)
                .firstName(normalize(request.firstName()))
                .lastName(normalize(request.lastName()))
                .phoneNumber(normalizePhone(request.phoneNumber()))
                .email(normalizeEmail(request.email()))
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .experienceYears(request.experienceYears())
                .bio(request.bio())
                .hourlyRate(request.hourlyRate())
                .category(category)
                .skills(new LinkedHashSet<>(skills))
                .build();

        Worker savedWorker = workerRepository.save(worker);

        sendVerificationEmail(userAccount);

        return buildAuthResponse(userAccount, savedWorker.getId());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserAccount userAccount = findByIdentifier(request.identifier())
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        userAccount.setLastLoginAt(Instant.now());
        userAccountRepository.save(userAccount);

        UUID profileId = resolveProfileId(userAccount);
        return buildAuthResponse(userAccount, profileId);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = tokenHashService.hash(request.refreshToken());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        RefreshToken refreshToken = tokenOpt.get();

        // Replay protection: if token is already revoked, an attacker or compromised client reused it!
        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            refreshTokenRepository.revokeAllByUserAccountId(refreshToken.getUserAccount().getId(), Instant.now());
            throw new InvalidTokenException("Refresh token has already been revoked. All sessions revoked for security.");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        UserAccount userAccount = refreshToken.getUserAccount();
        if (!Boolean.TRUE.equals(userAccount.getActive())) {
            throw new InvalidCredentialsException();
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        UUID profileId = resolveProfileId(userAccount);
        return buildAuthResponse(userAccount, profileId);
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        String tokenHash = tokenHashService.hash(request.refreshToken());
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        UserAccount userAccount = userAccountRepository.findById(currentUser.userAccountId())
                .orElseThrow(InvalidCredentialsException::new);

        UUID customerId = customerRepository.findByUserAccountId(userAccount.getId())
                .map(Customer::getId)
                .orElse(null);
        UUID workerId = workerRepository.findByUserAccountId(userAccount.getId())
                .map(Worker::getId)
                .orElse(null);

        return new CurrentUserResponse(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getPhoneNumber(),
                userAccount.getRole(),
                customerId,
                workerId
        );
    }

    private AuthResponse buildAuthResponse(UserAccount userAccount, UUID profileId) {
        String accessToken = jwtService.generateAccessToken(userAccount);
        String refreshTokenValue = UUID.randomUUID().toString();

        refreshTokenRepository.save(RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash(tokenHashService.hash(refreshTokenValue))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build());

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                jwtService.getAccessTokenExpirationMs(),
                userAccount.getRole(),
                profileId
        );
    }

    private UUID resolveProfileId(UserAccount userAccount) {
        return switch (userAccount.getRole()) {
            case CUSTOMER -> customerRepository.findByUserAccountId(userAccount.getId())
                    .map(Customer::getId)
                    .orElse(null);
            case WORKER -> workerRepository.findByUserAccountId(userAccount.getId())
                    .map(Worker::getId)
                    .orElse(null);
            case ADMIN -> userAccount.getId();
        };
    }

    private java.util.Optional<UserAccount> findByIdentifier(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim();
        return userAccountRepository.findByEmailIgnoreCase(normalized)
                .or(() -> userAccountRepository.findByPhoneNumber(normalizePhone(normalized)));
    }

    private void validateUserUniqueness(String email, String phoneNumber) {
        if (userAccountRepository.existsByEmailIgnoreCase(normalizeEmail(email))) {
            throw new UserAlreadyExistsException("An account with this email already exists");
        }
        if (userAccountRepository.existsByPhoneNumber(normalizePhone(phoneNumber))) {
            throw new UserAlreadyExistsException("An account with this phone number already exists");
        }
    }

    private void validateWorkerContactAvailable(String phoneNumber, String email) {
        if (workerRepository.existsByPhoneNumber(normalizePhone(phoneNumber))) {
            throw new WorkerAlreadyExistsException("phone number", phoneNumber);
        }
        if (email != null && !email.isBlank() && workerRepository.existsByEmailIgnoreCase(normalizeEmail(email))) {
            throw new WorkerAlreadyExistsException("email", email);
        }
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Set<Skill> findSkills(Set<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Set.of();
        }

        List<Skill> skills = skillRepository.findAllById(skillIds);
        if (skills.size() != skillIds.size()) {
            throw new SkillNotFoundException("One or more skills were not found");
        }
        return new LinkedHashSet<>(skills);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        userAccountRepository.findByEmailIgnoreCase(email)
                .ifPresent(userAccount -> {
                    if (Boolean.TRUE.equals(userAccount.getActive())) {
                        String resetToken = jwtService.generatePasswordResetToken(userAccount.getId());
                        userAccount.setPasswordResetTokenHash(tokenHashService.hash(resetToken));
                        userAccount.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
                        userAccountRepository.saveAndFlush(userAccount);

                        notificationService.sendEmail(
                                userAccount.getEmail(),
                                "Password Reset Request",
                                "<p>You requested a password reset. Use this token: <code>" + resetToken + "</code></p>"
                        );
                    }
                });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        Claims claims;
        try {
            claims = jwtService.validatePasswordResetToken(request.token());
        } catch (JwtException ex) {
            throw new InvalidTokenException("Invalid or expired password reset token");
        }

        UUID userAccountId = UUID.fromString(claims.getSubject());
        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (!Boolean.TRUE.equals(userAccount.getActive())) {
            throw new InvalidTokenException("Account is disabled");
        }

        String presentedTokenHash = tokenHashService.hash(request.token());
        if (userAccount.getPasswordResetTokenHash() == null
                || !userAccount.getPasswordResetTokenHash().equals(presentedTokenHash)) {
            throw new InvalidTokenException("Password reset token has already been used or is invalid");
        }

        if (userAccount.getPasswordResetTokenExpiresAt() != null
                && userAccount.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Password reset token has expired");
        }

        userAccount.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccount.setPasswordResetTokenHash(null);
        userAccount.setPasswordResetTokenExpiresAt(null);

        Instant now = Instant.now();
        userAccount.setPasswordChangedAt(now);
        userAccountRepository.saveAndFlush(userAccount);

        refreshTokenRepository.revokeAllByUserAccountId(userAccount.getId(), now);

        notificationService.notifyUser(
                userAccount.getId(),
                NotificationType.ACCOUNT_PASSWORD_CHANGED,
                NotificationChannel.BOTH,
                "Password Changed",
                "Your password has been changed successfully.",
                "USER",
                userAccount.getId()
        );
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        UserAccount userAccount = userAccountRepository.findById(currentUser.userAccountId())
                .orElseThrow(() -> new UserNotFoundException("User account not found"));

        if (!passwordEncoder.matches(request.currentPassword(), userAccount.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        userAccount.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        Instant now = Instant.now();
        userAccount.setPasswordChangedAt(now);
        userAccountRepository.saveAndFlush(userAccount);

        refreshTokenRepository.revokeAllByUserAccountId(userAccount.getId(), now);

        notificationService.notifyUser(
                userAccount.getId(),
                NotificationType.ACCOUNT_PASSWORD_CHANGED,
                NotificationChannel.BOTH,
                "Password Changed",
                "Your password has been changed successfully.",
                "USER",
                userAccount.getId()
        );
    }

    @Override
    public void verifyEmail(VerifyEmailRequest request) {
        Claims claims;
        try {
            claims = jwtService.validateEmailVerificationToken(request.token());
        } catch (JwtException ex) {
            throw new InvalidTokenException("Invalid or expired email verification token");
        }

        UUID userAccountId = UUID.fromString(claims.getSubject());
        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElseThrow(() -> new InvalidTokenException("Invalid email verification token"));

        userAccount.setEmailVerified(true);
        userAccountRepository.save(userAccount);
    }

    @Override
    public void resendVerificationEmail(ResendVerificationEmailRequest request) {
        userAccountRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .ifPresent(userAccount -> {
                    if (!Boolean.TRUE.equals(userAccount.getEmailVerified())
                            && Boolean.TRUE.equals(userAccount.getActive())) {
                        sendVerificationEmail(userAccount);
                    }
                });
    }

    private void sendVerificationEmail(UserAccount userAccount) {
        String token = jwtService.generateEmailVerificationToken(userAccount.getId());
        notificationService.sendEmail(
                userAccount.getEmail(),
                "Verify your email address",
                "<p>Welcome to BlueCollar! Please verify your email using this token: <code>" + token + "</code></p>"
        );
    }
}
