package com.bluecollar.auth.service;

import com.bluecollar.auth.dto.*;
import com.bluecollar.auth.entity.RefreshToken;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.exception.InvalidCredentialsException;
import com.bluecollar.auth.exception.InvalidTokenException;
import com.bluecollar.auth.exception.UserAlreadyExistsException;
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
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.worker.entity.Worker;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenHashService tokenHashService;

    @InjectMocks
    private AuthServiceImpl authService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID userAccountId;
    private UUID customerId;
    private UUID workerId;
    private UUID categoryId;
    private UserAccount userAccount;
    private Customer customer;
    private Worker worker;
    private Category category;

    @BeforeEach
    void setUp() {
        userAccountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        userAccount = UserAccount.builder()
                .email("john@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
        userAccount.setId(userAccountId);

        customer = Customer.builder()
                .userAccount(userAccount)
                .firstName("John")
                .lastName("Doe")
                .build();
        customer.setId(customerId);

        category = Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build();
        category.setId(categoryId);

        worker = Worker.builder()
                .userAccount(userAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("9876543211")
                .email("worker@example.com")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .build();
        worker.setId(workerId);

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void registerCustomerShouldCreateAccountAndReturnAuthResponse() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(
                "john@example.com", "9876543210", "Password@1", "John", "Doe"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userAccountRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("Password@1")).thenReturn("hashed_password");
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(userAccount);
        when(customerMapper.toEntity(userAccount, "John", "Doe")).thenReturn(customer);
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);
        when(jwtService.generateAccessToken(userAccount)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(tokenHashService.hash(anyString())).thenReturn("refresh-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse result = authService.registerCustomer(request);

        assertEquals("access-token", result.accessToken());
        assertEquals(UserRole.CUSTOMER, result.role());
        assertEquals(customerId, result.profileId());
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(customerRepository).save(any(Customer.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void registerCustomerShouldThrowWhenEmailAlreadyExists() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(
                "john@example.com", "9876543210", "Password@1", "John", "Doe"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.registerCustomer(request));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void registerCustomerShouldThrowWhenPhoneAlreadyExists() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(
                "john@example.com", "9876543210", "Password@1", "John", "Doe"
        );

        when(userAccountRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userAccountRepository.existsByPhoneNumber("9876543210")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.registerCustomer(request));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void registerWorkerShouldCreateAccountAndReturnAuthResponse() {
        RegisterWorkerRequest request = new RegisterWorkerRequest(
                "worker@example.com", "9876543211", "Password@1", "Bob", "Builder",
                null, null, null, null, BigDecimal.valueOf(50.00), categoryId, Set.of()
        );

        when(userAccountRepository.existsByEmailIgnoreCase("worker@example.com")).thenReturn(false);
        when(userAccountRepository.existsByPhoneNumber("9876543211")).thenReturn(false);
        when(workerRepository.existsByPhoneNumber("9876543211")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("worker@example.com")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(passwordEncoder.encode("Password@1")).thenReturn("hashed_password");

        UserAccount workerAccount = UserAccount.builder()
                .email("worker@example.com")
                .phoneNumber("9876543211")
                .passwordHash("hashed_password")
                .role(UserRole.WORKER)
                .active(true)
                .build();
        workerAccount.setId(UUID.randomUUID());

        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(workerAccount);
        when(workerRepository.save(any(Worker.class))).thenReturn(worker);
        when(jwtService.generateAccessToken(workerAccount)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(tokenHashService.hash(anyString())).thenReturn("refresh-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse result = authService.registerWorker(request);

        assertEquals("access-token", result.accessToken());
        assertEquals(UserRole.WORKER, result.role());
        assertEquals(workerId, result.profileId());
        verify(workerRepository).save(any(Worker.class));
    }

    @Test
    void registerWorkerShouldThrowWhenCategoryNotFound() {
        RegisterWorkerRequest request = new RegisterWorkerRequest(
                "worker@example.com", "9876543211", "Password@1", "Bob", "Builder",
                null, null, null, null, BigDecimal.valueOf(50.00), categoryId, Set.of()
        );

        when(userAccountRepository.existsByEmailIgnoreCase("worker@example.com")).thenReturn(false);
        when(userAccountRepository.existsByPhoneNumber("9876543211")).thenReturn(false);
        when(workerRepository.existsByPhoneNumber("9876543211")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("worker@example.com")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> authService.registerWorker(request));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void loginShouldReturnAuthResponseWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("john@example.com", "Password@1");

        when(userAccountRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("Password@1", "hashed_password")).thenReturn(true);
        when(userAccountRepository.save(userAccount)).thenReturn(userAccount);
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(customer));
        when(jwtService.generateAccessToken(userAccount)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(tokenHashService.hash(anyString())).thenReturn("refresh-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse result = authService.login(request);

        assertEquals("access-token", result.accessToken());
        assertEquals(UserRole.CUSTOMER, result.role());
        assertEquals(customerId, result.profileId());
        verify(userAccountRepository).save(userAccount);
    }

    @Test
    void loginShouldThrowWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPass1!");

        when(userAccountRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("WrongPass1!", "hashed_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginShouldThrowWhenAccountIsInactive() {
        LoginRequest request = new LoginRequest("john@example.com", "Password@1");
        userAccount.setActive(false);

        when(userAccountRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(userAccount));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void refreshTokenShouldReturnNewAuthResponseWhenTokenIsValid() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash("token-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(tokenHashService.hash(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return token.equals("valid-refresh-token") ? "token-hash" : "new-refresh-hash";
        });
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("token-hash")).thenReturn(Optional.of(refreshToken));
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(customer));
        when(jwtService.generateAccessToken(userAccount)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse result = authService.refreshToken(request);

        assertEquals("new-access-token", result.accessToken());
        assertTrue(refreshToken.getRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void refreshTokenShouldThrowWhenTokenIsExpired() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash("token-hash")
                .expiresAt(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();

        when(tokenHashService.hash("expired-refresh-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("token-hash")).thenReturn(Optional.of(refreshToken));

        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshTokenShouldThrowWhenTokenNotFound() {
        RefreshTokenRequest request = new RefreshTokenRequest("unknown-token");

        when(tokenHashService.hash("unknown-token")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("unknown-hash")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    void logoutShouldRevokeRefreshTokenWhenTokenExists() {
        RefreshTokenRequest request = new RefreshTokenRequest("logout-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash("logout-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(tokenHashService.hash("logout-token")).thenReturn("logout-hash");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("logout-hash")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(refreshToken)).thenReturn(refreshToken);

        authService.logout(request);

        assertTrue(refreshToken.getRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void logoutShouldDoNothingWhenTokenNotFound() {
        RefreshTokenRequest request = new RefreshTokenRequest("missing-token");

        when(tokenHashService.hash("missing-token")).thenReturn("missing-hash");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("missing-hash")).thenReturn(Optional.empty());

        authService.logout(request);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void getCurrentUserShouldReturnCurrentUserResponseForCustomer() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                userAccountId, "john@example.com", UserRole.CUSTOMER
        );

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(authenticatedUser);
        when(userAccountRepository.findById(userAccountId)).thenReturn(Optional.of(userAccount));
        when(customerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(customer));
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.empty());

        CurrentUserResponse result = authService.getCurrentUser();

        assertEquals(userAccountId, result.userAccountId());
        assertEquals("john@example.com", result.email());
        assertEquals("9876543210", result.phoneNumber());
        assertEquals(UserRole.CUSTOMER, result.role());
        assertEquals(customerId, result.customerId());
        assertEquals(null, result.workerId());
    }

    @Test
    void getCurrentUserShouldThrowWhenUserAccountNotFound() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                userAccountId, "john@example.com", UserRole.CUSTOMER
        );

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(authenticatedUser);
        when(userAccountRepository.findById(userAccountId)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.getCurrentUser());
    }
}
