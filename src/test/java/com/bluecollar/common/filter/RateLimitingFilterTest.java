package com.bluecollar.common.filter;

import com.bluecollar.common.security.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitService rateLimitService;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        filter = new RateLimitingFilter(rateLimitService, "127.0.0.1,10.0.0.1");
    }

    @Test
    void resolvesClientIpFromForwardedHeaderWhenProxyIsTrusted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted proxy
        request.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimitService.allowRequest("203.0.113.195")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(rateLimitService).allowRequest("203.0.113.195");
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresForwardedHeaderWhenRemoteAddrIsNotTrustedProxy() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.44"); // Untrusted public IP
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // Spoofed header
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimitService.allowRequest("198.51.100.44")).thenReturn(true);

        filter.doFilter(request, response, chain);

        // Must rate-limit on actual remote IP, NOT the spoofed header
        verify(rateLimitService).allowRequest("198.51.100.44");
        verify(rateLimitService, never()).allowRequest("1.2.3.4");
        verify(chain).doFilter(request, response);
    }

    @Test
    void returns429WhenRateLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimitService.allowRequest("198.51.100.50")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
}
