package com.bluecollar.auth.controller;

import com.bluecollar.admin.service.AdminUserService;
import com.bluecollar.auth.dto.*;
import com.bluecollar.auth.entity.RefreshToken;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.RefreshTokenRepository;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.security.JwtService;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationLifecycleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private TestDbCleanup testDbCleanup;

    @Value("${bluecollar.auth.jwt.secret:default-secret-key-must-be-very-long-and-secure-at-least-256-bits!}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        testDbCleanup.cleanAuthArtifacts();
        emailOutboxRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        testDbCleanup.cleanAuthArtifacts();
    }

    @Test
    void registrationVerificationSucceedsOnlyWithValidToken() throws Exception {
        // 1. Register customer
        MvcResult registerResult = registerUser("verifytest@example.com", "9111222333", "Password@1");
        UserAccount account = userAccountRepository.findByEmailIgnoreCase("verifytest@example.com").orElseThrow();
        assertFalse(account.getEmailVerified());

        // Check verification email was queued
        List<EmailOutbox> emails = emailOutboxRepository.findAll();
        assertFalse(emails.isEmpty());
        assertTrue(emails.stream().anyMatch(e -> e.getRecipientEmail().equals("verifytest@example.com")));

        // 2. Verification fails with invalid/tampered token
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "invalid.jwt.token"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        // 3. Verification succeeds with valid token
        String validToken = jwtService.generateEmailVerificationToken(account.getId());
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(validToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));

        UserAccount updated = userAccountRepository.findById(account.getId()).orElseThrow();
        assertTrue(updated.getEmailVerified());
    }

    @Test
    void registrationVerificationFailsWithExpiredToken() throws Exception {
        UserAccount account = userAccountRepository.save(UserAccount.builder()
                .email("expiredtoken@example.com")
                .phoneNumber("9222333444")
                .passwordHash("hashed")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        String expiredToken = createExpiredJwt(account.getId(), "email_verification");

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordResponsesDoNotRevealAccountExistence() throws Exception {
        registerUser("realuser@example.com", "9333444555", "Password@1");
        emailOutboxRepository.deleteAll();

        // 1. Existing user
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "realuser@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If an account with this email exists, a password reset token has been sent"));

        // Email was sent for real user
        assertEquals(1, emailOutboxRepository.findAll().size());
        assertEquals("realuser@example.com", emailOutboxRepository.findAll().get(0).getRecipientEmail());

        // 2. Non-existent user
        emailOutboxRepository.deleteAll();
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ghost@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If an account with this email exists, a password reset token has been sent"));

        // No email sent for ghost
        assertTrue(emailOutboxRepository.findAll().isEmpty());
    }

    @Test
    void passwordResetLifecycleAndReusedTokenFails() throws Exception {
        registerUser("resetreuse@example.com", "9444555666", "Password@1");
        UserAccount account = userAccountRepository.findByEmailIgnoreCase("resetreuse@example.com").orElseThrow();

        // Request reset
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "resetreuse@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        // Retrieve generated token from email outbox
        EmailOutbox email = emailOutboxRepository.findAll().stream()
                .filter(e -> e.getRecipientEmail().equals("resetreuse@example.com") && e.getSubject().contains("Password Reset"))
                .findFirst().orElseThrow();
        String body = email.getBodyHtml();
        String resetToken = body.substring(body.indexOf("<code>") + 6, body.indexOf("</code>"));

        // 1. Reset succeeds first time
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword@1"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        // 2. REUSED token must fail!
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "AnotherPassword@2"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isUnauthorized());

        // 3. Old password cannot login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "resetreuse@example.com",
                                  "password": "Password@1"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        // 4. New password can login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "resetreuse@example.com",
                                  "password": "NewPassword@1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void passwordResetFailsWithExpiredToken() throws Exception {
        UserAccount account = userAccountRepository.save(UserAccount.builder()
                .email("expiredreset@example.com")
                .phoneNumber("9555666777")
                .passwordHash("hashed")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        String expiredToken = createExpiredJwt(account.getId(), "password_reset");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword@1"
                                }
                                """.formatted(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsRevokedAfterPasswordChange() throws Exception {
        MvcResult registerResult = registerUser("sessionrevoked@example.com", "9666777888", "Password@1");
        String oldAccessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.accessToken");
        String oldRefreshToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.refreshToken");

        // Verify old access token works initially
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isOk());

        // Change password
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password@1",
                                  "newPassword": "NewPassword@1"
                                }
                                """))
                .andExpect(status().isOk());

        // Old refresh token must be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        // Old access token issued before password change is now revoked!
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isForbidden());

        // Login with new password gives active token
        MvcResult newLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "sessionrevoked@example.com",
                                  "password": "NewPassword@1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = JsonPath.read(newLoginResult.getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedUsersAreDeniedAccessWithExistingJwt() throws Exception {
        MvcResult registerResult = registerUser("deactivated@example.com", "9777888999", "Password@1");
        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.accessToken");
        String refreshToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.refreshToken");

        // Can access initially
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Deactivate user
        UserAccount account = userAccountRepository.findByEmailIgnoreCase("deactivated@example.com").orElseThrow();
        account.setActive(false);
        userAccountRepository.save(account);

        // Deactivated user cannot use existing JWT!
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        // Deactivated user cannot refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesTakeEffectImmediatelyWithoutWaitingForJwtExpiry() throws Exception {
        // Register customer
        MvcResult registerResult = registerUser("promoteme@example.com", "9888999000", "Password@1");
        String originalJwt = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.accessToken");

        // Customer role cannot access admin endpoint
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + originalJwt))
                .andExpect(status().isForbidden());

        // Update role to ADMIN in DB
        UserAccount account = userAccountRepository.findByEmailIgnoreCase("promoteme@example.com").orElseThrow();
        account.setRole(UserRole.ADMIN);
        userAccountRepository.save(account);

        // Without re-issuing JWT, the SAME JWT now reflects ADMIN role immediately!
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + originalJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshTokenRotationAndReplayProtection() throws Exception {
        MvcResult loginResult = registerUser("rotation@example.com", "9999000111", "Password@1");
        String r1 = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.refreshToken");

        // 1. Rotate R1 to get R2
        MvcResult refresh1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(r1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String r2 = JsonPath.read(refresh1.getResponse().getContentAsString(), "$.data.refreshToken");
        assertNotEquals(r1, r2);

        // 2. Replay attack: client attempts to reuse already rotated R1
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(r1)))
                .andExpect(status().isUnauthorized());

        // 3. Replay protection: R2 must now also be revoked due to replay detection!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(r2)))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult registerUser(String email, String phone, String password) throws Exception {
        String payload = """
                {
                  "email": "%s",
                  "phoneNumber": "%s",
                  "password": "%s",
                  "firstName": "Test",
                  "lastName": "User"
                }
                """.formatted(email, phone, password);

        return mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String createExpiredJwt(UUID subject, String type) {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = Arrays.copyOf(bytes, Math.max(32, bytes.length));
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64.getEncoder().encodeToString(padded)));

        Date past = new Date(System.currentTimeMillis() - 7200000);
        Date expiry = new Date(System.currentTimeMillis() - 3600000);

        return Jwts.builder()
                .subject(subject.toString())
                .claim("type", type)
                .issuedAt(past)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
