package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.AdminUserResponse;
import com.bluecollar.audit.service.AuditLogService;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final com.bluecollar.auth.repository.RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UserRole role, Boolean active, String email, Pageable pageable) {
        return userAccountRepository.findWithFilters(role, active, email, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID id) {
        return toResponse(findUser(id));
    }

    @Override
    public AdminUserResponse activateUser(UUID id) {
        UserAccount user = findUser(id);
        user.setActive(true);
        UserAccount saved = userAccountRepository.save(user);
        auditLogService.logCurrentAction("USER_ACTIVATED", "USER", saved.getId(),
                java.util.Map.of("active", false), java.util.Map.of("active", true));
        return toResponse(saved);
    }

    @Override
    public AdminUserResponse deactivateUser(UUID id) {
        UserAccount user = findUser(id);
        if (user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getActive())) {
            long activeAdminCount = userAccountRepository.countByRoleAndActiveTrue(UserRole.ADMIN);
            if (activeAdminCount <= 1) {
                throw new IllegalStateException("Cannot deactivate the last active administrator");
            }
        }
        user.setActive(false);
        refreshTokenRepository.revokeAllByUserAccountId(user.getId(), java.time.Instant.now());
        UserAccount saved = userAccountRepository.save(user);
        auditLogService.logCurrentAction("USER_DEACTIVATED", "USER", saved.getId(),
                java.util.Map.of("active", true), java.util.Map.of("active", false));
        return toResponse(saved);
    }

    @Override
    public AdminUserResponse changeRole(UUID id, UserRole role) {
        UserAccount user = findUser(id);
        UUID currentAdminId = SecurityUtils.getCurrentUser().userAccountId();
        if (user.getId().equals(currentAdminId)) {
            throw new UnauthorizedException("You cannot change your own role");
        }
        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN
                && Boolean.TRUE.equals(user.getActive())
                && userAccountRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new IllegalStateException("Cannot demote the last active administrator");
        }
        UserRole oldRole = user.getRole();
        user.setRole(role);
        UserAccount saved = userAccountRepository.save(user);
        auditLogService.logCurrentAction("USER_ROLE_CHANGED", "USER", saved.getId(),
                java.util.Map.of("role", oldRole.name()), java.util.Map.of("role", role.name()));
        return toResponse(saved);
    }

    private UserAccount findUser(UUID id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private AdminUserResponse toResponse(UserAccount user) {
        UUID profileId = null;
        String profileType = null;
        if (user.getRole() == UserRole.CUSTOMER) {
            profileId = customerRepository.findByUserAccountId(user.getId()).map(c -> c.getId()).orElse(null);
            profileType = "CUSTOMER";
        } else if (user.getRole() == UserRole.WORKER) {
            profileId = workerRepository.findByUserAccountId(user.getId()).map(w -> w.getId()).orElse(null);
            profileType = "WORKER";
        }
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                Boolean.TRUE.equals(user.getActive()),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getLastLoginAt(),
                profileId,
                profileType
        );
    }
}
