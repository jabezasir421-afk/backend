package com.bluecollar.audit.controller;

import com.bluecollar.audit.entity.AuditLog;
import com.bluecollar.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAuditLogControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID actorUserId;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        auditLogRepository.deleteAll();

        actorUserId = UUID.randomUUID();
        entityId = UUID.randomUUID();

        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(actorUserId)
                .actorRole("ADMIN")
                .action("USER_ACTIVATED")
                .entityType("USER")
                .entityId(entityId)
                .oldValue(Map.of("active", false))
                .newValue(Map.of("active", true))
                .ipAddress("127.0.0.1")
                .correlationId("corr-001")
                .build());

        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(UUID.randomUUID())
                .actorRole("ADMIN")
                .action("WORKER_VERIFIED")
                .entityType("WORKER")
                .entityId(UUID.randomUUID())
                .build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchAuditLogsShouldReturnPaginatedResultsForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Audit logs fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchAuditLogsShouldFilterByEntityTypeAndEntityId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("entityType", "USER")
                        .param("entityId", entityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].action").value("USER_ACTIVATED"))
                .andExpect(jsonPath("$.data.content[0].entityType").value("USER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchAuditLogsShouldFilterByActorUserId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorUserId", actorUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].actorUserId").value(actorUserId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchAuditLogsShouldFilterByCreatedAtRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-01T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void searchAuditLogsShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void searchAuditLogsShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
