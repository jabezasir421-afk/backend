package com.bluecollar.admin.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminWorkerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private Worker worker;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        testDbCleanup.cleanCoreDomain();

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build());

        UserAccount userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker.admin@example.com")
                .phoneNumber("+919876543250")
                .passwordHash("hashed_password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount)
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+919876543251")
                .email("jane.smith@example.com")
                .gender(Gender.FEMALE)
                .dateOfBirth(LocalDate.of(1992, 5, 10))
                .experienceYears(4)
                .bio("Skilled plumber")
                .hourlyRate(new BigDecimal("45.00"))
                .available(true)
                .verified(true)
                .active(true)
                .category(category)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void verifyWorkerShouldReturnConflictWhenIdentityDocumentIsMissing() throws Exception {
        worker.setVerified(false);
        workerRepository.saveAndFlush(worker);

        mockMvc.perform(put("/api/v1/admin/workers/{id}/verify", worker.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Worker must have at least one verified identity document"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unverifyWorkerShouldUnverifyWorkerForAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/unverify", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker unverified successfully"))
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateWorkerShouldDeactivateWorkerForAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/deactivate", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker deactivated successfully"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void unverifyWorkerShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/unverify", worker.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void deactivateWorkerShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/deactivate", worker.getId()))
                .andExpect(status().isForbidden());
    }
}
