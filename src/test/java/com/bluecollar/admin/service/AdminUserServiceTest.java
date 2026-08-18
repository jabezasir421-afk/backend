package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.AdminUserResponse;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID userId;
    private UUID adminId;
    private UserAccount userAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userAccount = UserAccount.builder()
                .email("user@example.com")
                .phoneNumber("+919876543210")
                .passwordHash("hashed")
                .role(UserRole.CUSTOMER)
                .active(false)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        userAccount.setId(userId);
        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void activateUserShouldSetActiveTrue() {
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));
        when(userAccountRepository.save(userAccount)).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setActive(true);
            return saved;
        });

        AdminUserResponse result = adminUserService.activateUser(userId);

        assertTrue(result.active());
        verify(userAccountRepository).save(userAccount);
    }

    @Test
    void deactivateUserShouldSetActiveFalse() {
        userAccount.setActive(true);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));
        when(userAccountRepository.save(userAccount)).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse result = adminUserService.deactivateUser(userId);

        assertEquals(false, result.active());
        verify(userAccountRepository).save(userAccount);
    }

    @Test
    void changeRoleShouldUpdateRoleWhenAllowed() {
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(adminId, "admin@example.com", UserRole.ADMIN));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));
        when(userAccountRepository.save(userAccount)).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setRole(UserRole.WORKER);
            return saved;
        });

        AdminUserResponse result = adminUserService.changeRole(userId, UserRole.WORKER);

        assertEquals(UserRole.WORKER, result.role());
        verify(userAccountRepository).save(userAccount);
    }

    @Test
    void changeRoleShouldThrowWhenAdminTriesToChangeOwnRole() {
        userAccount.setRole(UserRole.ADMIN);
        userAccount.setId(adminId);
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(adminId, "admin@example.com", UserRole.ADMIN));
        when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(userAccount));

        assertThrows(UnauthorizedException.class, () -> adminUserService.changeRole(adminId, UserRole.CUSTOMER));
    }

    @Test
    void changeRoleShouldThrowWhenDemotingLastAdmin() {
        UserAccount lastAdmin = UserAccount.builder()
                .email("admin@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("hashed")
                .role(UserRole.ADMIN)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        lastAdmin.setId(userId);

        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(adminId, "other-admin@example.com", UserRole.ADMIN));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(lastAdmin));
        when(userAccountRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> adminUserService.changeRole(userId, UserRole.CUSTOMER));
    }
}
