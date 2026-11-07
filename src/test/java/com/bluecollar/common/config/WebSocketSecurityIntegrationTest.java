package com.bluecollar.common.config;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.security.JwtService;
import com.bluecollar.common.TestDbCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketSecurityIntegrationTest {

    @Autowired
    private WebSocketAuthChannelInterceptor interceptor;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private UserAccount userA;
    private UserAccount userB;
    private UserAccount adminUser;
    private String tokenA;
    private String tokenB;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        testDbCleanup.cleanCoreDomain();

        userA = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("userA@example.com")
                .phoneNumber("+919876543101")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        userB = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("userB@example.com")
                .phoneNumber("+919876543102")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        adminUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("admin@example.com")
                .phoneNumber("+919876543103")
                .passwordHash("hashed_password")
                .role(UserRole.ADMIN)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        tokenA = jwtService.generateAccessToken(userA);
        tokenB = jwtService.generateAccessToken(userB);
        tokenAdmin = jwtService.generateAccessToken(adminUser);
    }

    @Test
    void connectWithValidJwtShouldAuthenticateUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + tokenA);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser(), "Authenticated user principal must be set on CONNECT");
        assertTrue(resultAccessor.getUser().getName().contains(userA.getEmail()));
    }

    @Test
    void connectWithInvalidJwtShouldThrowBadCredentials() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-jwt-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void userCanSubscribeToOwnPrivateNotifications() {
        // 1. Connect user A
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + tokenA);
        connectAccessor.setLeaveMutable(true);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> connected = interceptor.preSend(connectMessage, null);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);

        // 2. Subscribe to own notifications topic: /topic/notifications/{userA.id}
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(connectedAccessor.getUser());
        subAccessor.setDestination("/topic/notifications/" + userA.getId());
        subAccessor.setLeaveMutable(true);
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> subResult = interceptor.preSend(subMessage, null);
        assertNotNull(subResult, "User must be allowed to subscribe to their own notifications");

        // 3. Subscribe to own personal user queue: /user/queue/notifications
        StompHeaderAccessor queueAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        queueAccessor.setUser(connectedAccessor.getUser());
        queueAccessor.setDestination("/user/queue/notifications");
        queueAccessor.setLeaveMutable(true);
        Message<byte[]> queueMessage = MessageBuilder.createMessage(new byte[0], queueAccessor.getMessageHeaders());

        Message<?> queueResult = interceptor.preSend(queueMessage, null);
        assertNotNull(queueResult, "User must be allowed to subscribe to their user queue");
    }

    @Test
    void unauthorizedUserCannotSubscribeToAnotherUsersPrivateNotifications() {
        // 1. Connect user A
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + tokenA);
        connectAccessor.setLeaveMutable(true);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> connected = interceptor.preSend(connectMessage, null);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);

        // 2. User A tries to subscribe to user B's notifications: /topic/notifications/{userB.id}
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(connectedAccessor.getUser());
        subAccessor.setDestination("/topic/notifications/" + userB.getId());
        subAccessor.setLeaveMutable(true);
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(subMessage, null),
                "User must NOT be allowed to subscribe to another user's notifications"
        );
        assertEquals("Cannot subscribe to another user's private notifications", ex.getMessage());
    }

    @Test
    void unauthenticatedUserCannotSubscribeToPrivateNotifications() {
        // Anonymous/unauthenticated subscription attempt
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/notifications/" + userA.getId());
        subAccessor.setLeaveMutable(true);
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(subMessage, null),
                "Unauthenticated user must NOT be allowed to subscribe to notifications"
        );
    }

    @Test
    void adminCanSubscribeToUserNotifications() {
        // Connect admin
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + tokenAdmin);
        connectAccessor.setLeaveMutable(true);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> connected = interceptor.preSend(connectMessage, null);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);

        // Admin subscribes to user A's notifications topic
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(connectedAccessor.getUser());
        subAccessor.setDestination("/topic/notifications/" + userA.getId());
        subAccessor.setLeaveMutable(true);
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> subResult = interceptor.preSend(subMessage, null);
        assertNotNull(subResult, "Admin should be permitted to monitor notification topics");
    }
}
