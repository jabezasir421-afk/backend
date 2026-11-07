package com.bluecollar.common.filter;

import com.bluecollar.common.security.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnBean(RateLimitService.class)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final Set<String> trustedProxies;

    public RateLimitingFilter(
            RateLimitService rateLimitService,
            @Value("${bluecollar.security.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1,localhost}") String trustedProxiesConfig
    ) {
        this.rateLimitService = rateLimitService;
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = resolveClientIp(request);
        if (!rateLimitService.allowRequest(clientKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the true client IP address safely:
     * - Only inspects forwarded headers (X-Forwarded-For, X-Real-IP) if the immediate remote address
     *   is in the configured list of trusted proxies.
     * - If the remote address is untrusted, forwarded headers are ignored to prevent IP spoofing attacks.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }

        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] ips = xForwardedFor.split(",");
                // Walk from right to left to find the first non-trusted proxy IP
                for (int i = ips.length - 1; i >= 0; i--) {
                    String candidate = ips[i].trim();
                    if (!candidate.isEmpty() && !isTrustedProxy(candidate)) {
                        return candidate;
                    }
                }
                // If all in header are trusted proxies, use the first client IP in the chain
                String leftmost = ips[0].trim();
                if (!leftmost.isEmpty()) {
                    return leftmost;
                }
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        return remoteAddr;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String cleanIp = ip.trim();
        if (trustedProxies.contains(cleanIp)) {
            return true;
        }
        // Also check private network subnets (RFC 1918 / loopback)
        return cleanIp.startsWith("127.")
                || cleanIp.startsWith("10.")
                || cleanIp.startsWith("192.168.")
                || cleanIp.startsWith("169.254.")
                || (cleanIp.startsWith("172.") && isPrivateClassB(cleanIp));
    }

    private boolean isPrivateClassB(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
