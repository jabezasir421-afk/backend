package com.bluecollar.notification.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.dto.NotificationPreferenceResponse;
import com.bluecollar.notification.dto.UpdateNotificationPreferenceRequest;
import com.bluecollar.notification.entity.*;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.notification.repository.NotificationPreferenceRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationChannelDeliveryIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private TestDbCleanup testDbCleanup;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private UserAccount userAccount;

    @BeforeEach
    void setUp() {
        testDbCleanup.cleanCoreDomain();
        notificationProperties.getEmail().setEnabled(true);
        reset(messagingTemplate);

        userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("delivery." + UUID.randomUUID() + "@example.com")
                .phoneNumber("+91" + (System.nanoTime() % 9000000000L + 1000000000L))
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        com.bluecollar.common.security.AuthenticatedUser authenticatedUser =
                new com.bluecollar.common.security.AuthenticatedUser(
                        userAccount.getId(),
                        userAccount.getEmail(),
                        userAccount.getRole()
                );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );
    }

    @Test
    void inAppDisabledAndEmailEnabled_emailStillSends() {
        // Disable in-app notifications, enable email booking updates
        notificationService.updatePreferences(new UpdateNotificationPreferenceRequest(
                true,   // emailBookingUpdates
                true,   // emailAccountUpdates
                true,   // emailReviewUpdates
                false   // inAppEnabled = false!
        ));

        // Trigger notification with channel = BOTH
        notificationService.notifyUser(
                userAccount.getId(),
                NotificationType.BOOKING_CREATED,
                NotificationChannel.BOTH,
                "Booking Confirmed",
                "Your booking has been created.",
                "BOOKING",
                UUID.randomUUID()
        );

        // 1. In-app notification should NOT be saved in user notifications
        long inAppCount = notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(userAccount.getId());
        assertEquals(0, inAppCount, "In-app notification must not be saved when in-app is disabled");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        // 2. Email outbox record MUST still be created and queued!
        List<EmailOutbox> outboxes = emailOutboxRepository.findAll();
        assertEquals(1, outboxes.size(), "Email must still be queued when in-app is disabled but email is enabled");
        EmailOutbox email = outboxes.getFirst();
        assertEquals(userAccount.getEmail(), email.getRecipientEmail());
        assertEquals("Booking Confirmed", email.getSubject());
        assertEquals(OutboxStatus.PENDING, email.getStatus());
    }

    @Test
    void inAppEnabledAndEmailDisabled_inAppNotificationStillSends() {
        // Enable in-app notifications, disable email booking updates
        notificationService.updatePreferences(new UpdateNotificationPreferenceRequest(
                false,  // emailBookingUpdates = false!
                false,  // emailAccountUpdates = false
                false,  // emailReviewUpdates = false
                true    // inAppEnabled = true!
        ));

        // Trigger notification with channel = BOTH
        notificationService.notifyUser(
                userAccount.getId(),
                NotificationType.BOOKING_CREATED,
                NotificationChannel.BOTH,
                "Booking Confirmed",
                "Your booking has been created.",
                "BOOKING",
                UUID.randomUUID()
        );

        // 1. In-app notification MUST be saved and broadcast via WebSocket
        long inAppCount = notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(userAccount.getId());
        assertEquals(1, inAppCount, "In-app notification must be saved when in-app is enabled");
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(userAccount.getId().toString()),
                eq("/queue/notifications"),
                any(Object.class)
        );

        // 2. Email outbox record MUST NOT be created
        List<EmailOutbox> outboxes = emailOutboxRepository.findAll();
        assertEquals(0, outboxes.size(), "Email must not be queued when email updates are disabled");
    }

    @Test
    void getPreferencesInReadOnlyTransactionDoesNotPerformDatabaseWrites() {
        // User has no preference record in DB yet
        assertFalse(preferenceRepository.findByUserAccountId(userAccount.getId()).isPresent());

        // Calling getPreferences in read-only transaction must succeed and return defaults
        NotificationPreferenceResponse response = notificationService.getPreferences();
        assertNotNull(response);
        assertTrue(response.inAppEnabled());
        assertTrue(response.emailBookingUpdates());

        // Still no record in DB (read-only query did not attempt to write)
        assertFalse(preferenceRepository.findByUserAccountId(userAccount.getId()).isPresent());
    }
}
