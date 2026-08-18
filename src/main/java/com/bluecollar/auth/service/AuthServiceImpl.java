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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
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
        return buildAuthResponse(userAccount, savedWorker.getId());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserAccount userAccount = findByIdentifier(request.identifier())
                .filter(account -> account.getActive())
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
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        UserAccount userAccount = refreshToken.getUserAccount();
        if (!userAccount.getActive()) {
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
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.startsWith("+91")) {
            return trimmed;
        }
        return trimmed;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userAccountRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new UserNotFoundException("User with email " + request.email() + " not found"));
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        try {
            Claims claims = jwtService.validatePasswordResetToken(request.token());
            UUID userAccountId = UUID.fromString(claims.getSubject());

            UserAccount userAccount = userAccountRepository.findById(userAccountId)
                    .orElseThrow(() -> new UserNotFoundException("User account not found"));

            userAccount.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            userAccountRepository.save(userAccount);
        } catch (JwtException ex) {
            throw new InvalidTokenException("Invalid or expired password reset token");
        }
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
        userAccountRepository.save(userAccount);
    }
}
