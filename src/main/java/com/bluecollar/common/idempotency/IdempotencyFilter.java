package com.bluecollar.common.idempotency;

import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            return true;
        }
        return !MUTATING_METHODS.contains(request.getMethod().toUpperCase());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER).trim();
        String userScope = resolveUserScope(request);
        String compositeKey = userScope + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + idempotencyKey;

        Optional<CachedHttpResponse> cachedOpt = idempotencyService.get(compositeKey);
        if (cachedOpt.isPresent()) {
            CachedHttpResponse cached = cachedOpt.get();
            log.debug("Idempotent replay served for key: {}", compositeKey);
            response.setStatus(cached.statusCode());
            if (cached.contentType() != null) {
                response.setContentType(cached.contentType());
            }
            response.setHeader("X-Idempotency-Status", "REPLAYED");
            response.getOutputStream().write(cached.body());
            response.getOutputStream().flush();
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);

        int status = responseWrapper.getStatus();
        // Only cache successful idempotent operations (2xx)
        if (status >= 200 && status < 300) {
            byte[] body = responseWrapper.getContentAsByteArray();
            idempotencyService.put(compositeKey, new CachedHttpResponse(
                    status,
                    responseWrapper.getContentType(),
                    body
            ));
            responseWrapper.setHeader("X-Idempotency-Status", "SAVED");
        }

        responseWrapper.copyBodyToResponse();
    }

    private String resolveUserScope(HttpServletRequest request) {
        try {
            AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser != null && currentUser.userAccountId() != null) {
                return currentUser.userAccountId().toString();
            }
        } catch (Exception ignored) {
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "anonymous";
    }
}
