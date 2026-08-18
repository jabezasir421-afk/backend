package com.bluecollar.notification.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.dto.NotificationPreferenceResponse;
import com.bluecollar.notification.dto.NotificationResponse;
import com.bluecollar.notification.dto.UpdateNotificationPreferenceRequest;
import com.bluecollar.notification.entity.*;
import com.bluecollar.notification.exception.NotificationNotFoundException;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.notification.repository.NotificationPreferenceRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private EmailOutboxRepository emailOutboxRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private NotificationProperties notificationProperties;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID userId;
    private UUID otherUserId;
    private UUID notificationId;
    private UUID referenceId;
    private AuthenticatedUser authenticatedUser;
    private Notification notification;
    private NotificationResponse notificationResponse;
    private NotificationPreference preference;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        referenceId = UUID.randomUUID();

        authenticatedUser = new AuthenticatedUser(userId, "user@example.com", UserRole.CUSTOMER);

        notification = Notification.builder()
                .recipientUserId(userId)
                .type(NotificationType.BOOKING_CREATED)
                .channel(NotificationChannel.IN_APP)
                .title("Booking created")
                .body("Your booking was created")
                .referenceType("BOOKING")
                .referenceId(referenceId)
                .build();
        notification.setId(notificationId);
        notification.setCreatedAt(Instant.now());

        notificationResponse = new NotificationResponse(
                notificationId,
                "BOOKING_CREATED",
                "Booking created",
                "Your booking was created",
                "BOOKING",
                referenceId,
                null,
                notification.getCreatedAt()
        );

        preference = NotificationPreference.builder()
                .userAccountId(userId)
                .emailBookingUpdates(true)
                .emailAccountUpdates(true)
                .emailReviewUpdates(true)
                .inAppEnabled(true)
                .build();

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(authenticatedUser);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void getMyNotificationsShouldReturnPageOfResponses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);

        when(notificationRepository.findByRecipientUserIdAndActiveTrueOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(page);

        Page<NotificationResponse> result = notificationService.getMyNotifications(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(notificationId, result.getContent().getFirst().id());
        verify(notificationRepository).findByRecipientUserIdAndActiveTrueOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void getUnreadCountShouldReturnUnreadNotificationCount() {
        when(notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(userId)).thenReturn(3L);

        long count = notificationService.getUnreadCount();

        assertEquals(3L, count);
        verify(notificationRepository).countByRecipientUserIdAndActiveTrueAndReadAtIsNull(userId);
    }

    @Test
    void markAsReadShouldMarkNotificationAsReadWhenOwner() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        notificationService.markAsRead(notificationId);

        verify(notificationRepository).save(notification);
        org.junit.jupiter.api.Assertions.assertNotNull(notification.getReadAt());
    }

    @Test
    void markAsReadShouldThrowWhenNotificationNotFound() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> notificationService.markAsRead(notificationId));
    }

    @Test
    void markAsReadShouldThrowWhenUserIsNotOwner() {
        notification.setRecipientUserId(otherUserId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThrows(UnauthorizedException.class, () -> notificationService.markAsRead(notificationId));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsReadShouldMarkAllNotificationsAsRead() {
        Instant readAt = Instant.now();

        notificationService.markAllAsRead();

        verify(notificationRepository).markAllRead(eq(userId), any(Instant.class));
    }

    @Test
    void getPreferencesShouldReturnExistingPreferences() {
        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.of(preference));

        NotificationPreferenceResponse result = notificationService.getPreferences();

        assertEquals(true, result.emailBookingUpdates());
        assertEquals(true, result.emailAccountUpdates());
        assertEquals(true, result.emailReviewUpdates());
        assertEquals(true, result.inAppEnabled());
    }

    @Test
    void getPreferencesShouldCreateDefaultPreferencesWhenNoneExist() {
        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(preference);

        NotificationPreferenceResponse result = notificationService.getPreferences();

        assertEquals(true, result.inAppEnabled());
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void updatePreferencesShouldUpdateAndReturnPreferences() {
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest(
                false, true, false, false
        );

        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(preference)).thenReturn(preference);

        NotificationPreferenceResponse result = notificationService.updatePreferences(request);

        assertEquals(false, preference.getEmailBookingUpdates());
        assertEquals(true, preference.getEmailAccountUpdates());
        assertEquals(false, preference.getEmailReviewUpdates());
        assertEquals(false, preference.getInAppEnabled());
        assertEquals(false, result.inAppEnabled());
        verify(preferenceRepository).save(preference);
    }

    @Test
    void notifyUserShouldSaveNotificationWhenInAppEnabled() {
        NotificationProperties.Email emailProperties = new NotificationProperties.Email();
        emailProperties.setEnabled(false);
        when(notificationProperties.getEmail()).thenReturn(emailProperties);

        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyUser(
                userId,
                NotificationType.BOOKING_CREATED,
                NotificationChannel.IN_APP,
                "Booking created",
                "Your booking was created",
                "BOOKING",
                referenceId
        );

        verify(notificationRepository).save(any(Notification.class));
        verify(emailOutboxRepository, never()).save(any());
    }

    @Test
    void notifyUserShouldSkipInAppNotificationWhenInAppDisabled() {
        preference.setInAppEnabled(false);
        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.of(preference));

        notificationService.notifyUser(
                userId,
                NotificationType.BOOKING_CREATED,
                NotificationChannel.IN_APP,
                "Booking created",
                "Your booking was created",
                "BOOKING",
                referenceId
        );

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyUserShouldQueueEmailWhenEmailChannelEnabled() {
        NotificationProperties.Email emailProperties = new NotificationProperties.Email();
        emailProperties.setEnabled(true);
        when(notificationProperties.getEmail()).thenReturn(emailProperties);

        UserAccount userAccount = UserAccount.builder()
                .email("user@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .build();

        when(preferenceRepository.findByUserAccountId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));
        when(emailOutboxRepository.save(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyUser(
                userId,
                NotificationType.BOOKING_CREATED,
                NotificationChannel.EMAIL,
                "Booking created",
                "Your booking was created",
                "BOOKING",
                referenceId
        );

        verify(notificationRepository).save(any(Notification.class));
        verify(emailOutboxRepository).save(any(EmailOutbox.class));
    }
}
