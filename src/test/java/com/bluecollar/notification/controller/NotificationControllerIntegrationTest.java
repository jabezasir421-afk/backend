package com.bluecollar.notification.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.RefreshTokenRepository;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.notification.entity.Notification;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.notification.repository.NotificationPreferenceRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private UserAccount userAccount;
    private UsernamePasswordAuthenticationToken userAuth;
    private Notification unreadNotification;
    private Notification readNotification;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        testDbCleanup.cleanCoreDomain();

        userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("notify@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        unreadNotification = notificationRepository.saveAndFlush(buildNotification(userAccount.getId(), null));
        readNotification = notificationRepository.saveAndFlush(
                buildNotification(userAccount.getId(), java.time.Instant.now().minusSeconds(120))
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                userAccount.getId(),
                userAccount.getEmail(),
                UserRole.CUSTOMER
        );
        userAuth = new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    @Test
    void getNotificationsShouldReturnPaginatedNotificationsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notifications fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getUnreadCountShouldReturnUnreadCountForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Unread count fetched successfully"))
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    void markAsReadShouldMarkNotificationAsRead() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/{id}/read", unreadNotification.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification marked as read"));

        Notification updated = notificationRepository.findById(unreadNotification.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getReadAt());
    }

    @Test
    void markAllAsReadShouldMarkAllNotificationsAsRead() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        long unreadCount = notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(
                userAccount.getId()
        );
        org.junit.jupiter.api.Assertions.assertEquals(0L, unreadCount);
    }

    @Test
    void getPreferencesShouldReturnDefaultPreferencesForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Preferences fetched successfully"))
                .andExpect(jsonPath("$.data.emailBookingUpdates").value(true))
                .andExpect(jsonPath("$.data.inAppEnabled").value(true));
    }

    @Test
    void updatePreferencesShouldUpdatePreferencesForAuthenticatedUser() throws Exception {
        String payload = """
                {
                  "emailBookingUpdates": false,
                  "inAppEnabled": false
                }
                """;

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Preferences updated successfully"))
                .andExpect(jsonPath("$.data.emailBookingUpdates").value(false))
                .andExpect(jsonPath("$.data.inAppEnabled").value(false));
    }

    @Test
    void getNotificationsShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsReadShouldReturnNotFoundForMissingNotification() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/notifications/{id}/read", missingId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(userAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Notification not found with id: %s".formatted(missingId)
                ));
    }

    private Notification buildNotification(UUID recipientUserId, java.time.Instant readAt) {
        return Notification.builder()
                .recipientUserId(recipientUserId)
                .type(NotificationType.BOOKING_CREATED)
                .channel(NotificationChannel.IN_APP)
                .title("Booking created")
                .body("Your booking was created")
                .referenceType("BOOKING")
                .referenceId(UUID.randomUUID())
                .readAt(readAt)
                .active(true)
                .build();
    }
}
