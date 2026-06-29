package com.bluecollar.admin.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminDashboardControllerIntegrationTest {

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
    void getDashboardShouldReturnDashboardMetricsForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .param("fromDate", "2026-06-01")
                        .param("toDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dashboard fetched successfully"))
                .andExpect(jsonPath("$.data.totalCustomers").exists())
                .andExpect(jsonPath("$.data.totalWorkers").exists())
                .andExpect(jsonPath("$.data.totalBookings").exists())
                .andExpect(jsonPath("$.data.activeBookings").exists())
                .andExpect(jsonPath("$.data.completionRate").exists())
                .andExpect(jsonPath("$.data.categoriesDistribution").isArray())
                .andExpect(jsonPath("$.data.bookingsTrend").isArray());
    }

    @Test
    void getDashboardShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getDashboardShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
    }
}
