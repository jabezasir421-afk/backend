package com.bluecollar.analytics.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getOverviewShouldReturnAnalyticsOverviewForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Analytics overview fetched"))
                .andExpect(jsonPath("$.data.totalWorkers").exists())
                .andExpect(jsonPath("$.data.totalCustomers").exists())
                .andExpect(jsonPath("$.data.totalBookings").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRatingsShouldReturnRatingDistributionForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Rating distribution fetched"))
                .andExpect(jsonPath("$.data.distribution").exists())
                .andExpect(jsonPath("$.data.average").exists())
                .andExpect(jsonPath("$.data.totalReviews").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refreshShouldTriggerSnapshotRefreshForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/analytics/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Analytics snapshot refreshed"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDailyBookingsShouldReturnDailyStatsForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/bookings/daily")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Daily booking stats fetched"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getOverviewShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getOverviewShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isForbidden());
    }
}
