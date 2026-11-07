package com.bluecollar.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void propagatesExistingCorrelationIdHeader() throws ServletException, IOException {
        String existingId = "client-trace-12345";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workers");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValue.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals(existingId, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals(existingId, mdcValue.get(), "MDC must hold the correlation ID during request processing");
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC must be cleared after request completion");
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValue.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String generatedId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedId, "Correlation ID header must be populated in response");
        assertFalse(generatedId.isBlank());
        assertEquals(generatedId, mdcValue.get(), "MDC must hold the generated correlation ID");
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC must be cleared after request completion");
    }
}
