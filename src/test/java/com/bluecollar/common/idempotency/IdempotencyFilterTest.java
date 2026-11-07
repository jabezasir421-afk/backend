package com.bluecollar.common.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyFilterTest {

    private IdempotencyService idempotencyService;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(24, 1000);
        filter = new IdempotencyFilter(idempotencyService);
    }

    @Test
    void cachesSuccessfulPostRequestWithIdempotencyKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "req-12345");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
            httpRes.setStatus(201);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"bookingId\":\"abc-123\"}");
        };

        filter.doFilter(request, response, chain);

        assertEquals(201, response.getStatus());
        assertEquals("SAVED", response.getHeader("X-Idempotency-Status"));
        assertEquals("{\"bookingId\":\"abc-123\"}", response.getContentAsString());
    }

    @Test
    void replaysCachedResponseOnRepeatedRequestWithoutInvokingChain() throws ServletException, IOException {
        String key = "req-67890";
        FilterChain mockChain = mock(FilterChain.class);
        doAnswer(inv -> {
            jakarta.servlet.http.HttpServletResponse res = inv.getArgument(1);
            res.setStatus(200);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":\"SUCCESS\"}");
            return null;
        }).when(mockChain).doFilter(any(), any());

        // First call
        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        request1.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request1, response1, mockChain);

        assertEquals(200, response1.getStatus());
        assertEquals("SAVED", response1.getHeader("X-Idempotency-Status"));
        assertEquals("{\"status\":\"SUCCESS\"}", response1.getContentAsString());
        verify(mockChain, times(1)).doFilter(any(), any());

        // Second call with same idempotency key
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        request2.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, mockChain);

        assertEquals(200, response2.getStatus());
        assertEquals("REPLAYED", response2.getHeader("X-Idempotency-Status"));
        assertEquals("{\"status\":\"SUCCESS\"}", response2.getContentAsString());
        // Verify filterChain was NOT invoked a second time!
        verify(mockChain, times(1)).doFilter(any(), any());
    }

    @Test
    void ignoresGetRequestsEvenWithIdempotencyKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "req-get-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain mockChain = mock(FilterChain.class);

        filter.doFilter(request, response, mockChain);

        verify(mockChain).doFilter(request, response);
        assertNull(response.getHeader("X-Idempotency-Status"));
    }
}
