package com.bluecollar.common.filter;

import com.bluecollar.common.security.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(RateLimitService.class)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;


    @Override
    protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) throws jakarta.servlet.ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = request.getRemoteAddr();
        if (!rateLimitService.allowRequest(clientKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
