package com.bluecollar.common.config;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.security.JwtService;
import com.bluecollar.common.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern USER_DESTINATION_PATTERN = Pattern.compile("^/(topic|queue)/notifications/([^/]+)$");
    private static final Pattern USER_PREFIX_PATTERN = Pattern.compile("^/user/([^/]+)/.*$");

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication auth = authenticateToken(accessor);
            if (auth != null) {
                if (accessor.isMutable()) {
                    accessor.setUser(auth);
                } else {
                    StompHeaderAccessor mutable = StompHeaderAccessor.wrap(message);
                    mutable.setUser(auth);
                    return MessageBuilder.createMessage(message.getPayload(), mutable.getMessageHeaders());
                }
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private Authentication authenticateToken(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            log.debug("STOMP CONNECT without authentication token");
            return null;
        }

        try {
            Claims claims = jwtService.parseClaims(token);
            UUID userAccountId = UUID.fromString(claims.getSubject());

            UserAccount userAccount = userAccountRepository.findById(userAccountId).orElse(null);
            if (userAccount == null || !Boolean.TRUE.equals(userAccount.getActive())) {
                throw new BadCredentialsException("User account is inactive or not found");
            }

            if (userAccount.getPasswordChangedAt() != null) {
                Long tokenPwdChangedAt = claims.get("pwd_changed_at", Long.class);
                long currentPwdChangedAt = userAccount.getPasswordChangedAt().toEpochMilli();
                if (tokenPwdChangedAt == null || !tokenPwdChangedAt.equals(currentPwdChangedAt)) {
                    throw new BadCredentialsException("Session invalidated due to password change");
                }
            }

            UserRole currentRole = userAccount.getRole();
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    userAccount.getId(),
                    userAccount.getEmail(),
                    currentRole
            );

            log.debug("STOMP user authenticated: {}", authenticatedUser.email());
            return new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + currentRole.name()))
            );
        } catch (Exception ex) {
            log.warn("STOMP CONNECT authentication failed: {}", ex.getMessage());
            throw new BadCredentialsException("Invalid STOMP authentication token", ex);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Principal principal = accessor.getUser();
        AuthenticatedUser currentUser = extractAuthenticatedUser(principal);

        // Check for subscription to personal notifications: /user/queue/notifications
        if (destination.startsWith("/user/")) {
            if (currentUser == null) {
                throw new AccessDeniedException("Authentication required to subscribe to private user queues");
            }

            if (destination.startsWith("/user/queue/") || destination.startsWith("/user/topic/")) {
                // Standard personal destination for the authenticated user
                return;
            }

            Matcher prefixMatcher = USER_PREFIX_PATTERN.matcher(destination);
            if (prefixMatcher.matches()) {
                String targetUserId = prefixMatcher.group(1);
                if (!isAuthorizedForUser(currentUser, targetUserId)) {
                    throw new AccessDeniedException("Cannot subscribe to another user's private queue");
                }
            }
            return;
        }

        // Check for subscription to specific user topics: /topic/notifications/{userId} or /queue/notifications/{userId}
        Matcher matcher = USER_DESTINATION_PATTERN.matcher(destination);
        if (matcher.matches()) {
            if (currentUser == null) {
                throw new AccessDeniedException("Authentication required to subscribe to notifications");
            }

            String targetUserId = matcher.group(2);
            if (!isAuthorizedForUser(currentUser, targetUserId)) {
                throw new AccessDeniedException("Cannot subscribe to another user's private notifications");
            }
        }
    }

    private boolean isAuthorizedForUser(AuthenticatedUser currentUser, String targetUserId) {
        if (currentUser.role() == UserRole.ADMIN) {
            return true;
        }
        return currentUser.userAccountId().toString().equalsIgnoreCase(targetUserId);
    }

    private AuthenticatedUser extractAuthenticatedUser(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            authHeader = accessor.getFirstNativeHeader("authorization");
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String passcode = accessor.getPasscode();
        if (passcode != null && !passcode.isBlank()) {
            return passcode.trim();
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader.trim();
        }
        return null;
    }
}
