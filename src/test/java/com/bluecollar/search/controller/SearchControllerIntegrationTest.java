package com.bluecollar.search.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private AvailabilityService availabilityService;

    private Category category;
    private Worker verifiedWorker;
    private Worker unverifiedWorker;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        workerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-SearchCtrl")
                .description("Pipe services")
                .active(true)
                .build());

        UserAccount verifiedAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("verified@example.com")
                .phoneNumber("+1111111111")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        UserAccount unverifiedAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("unverified@example.com")
                .phoneNumber("+2222222222")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        verifiedWorker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(verifiedAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+0987654321")
                .email("bob@example.com")
                .primaryCity("New York")
                .primaryState("NY")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .averageRating(BigDecimal.valueOf(4.8))
                .experienceYears(8)
                .active(true)
                .verified(true)
                .available(true)
                .build());

        unverifiedWorker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(unverifiedAccount)
                .firstName("Jane")
                .lastName("Doe")
                .phoneNumber("+0123456789")
                .email("jane@example.com")
                .primaryCity("Boston")
                .primaryState("MA")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(40.00))
                .averageRating(BigDecimal.valueOf(3.5))
                .experienceYears(3)
                .active(true)
                .verified(false)
                .available(true)
                .build());

        when(availabilityService.isAvailableOnDate(any(), any())).thenReturn(true);
    }

    @Test
    void searchWorkersShouldReturnPaginatedVerifiedWorkers() throws Exception {
        mockMvc.perform(get("/api/v1/search/workers")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Workers fetched successfully"))
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.results[0].fullName").value("Bob Builder"))
                .andExpect(jsonPath("$.data.results[0].verified").value(true));
    }

    @Test
    void searchWorkersShouldFilterByCity() throws Exception {
        mockMvc.perform(get("/api/v1/search/workers")
                        .param("city", "New York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andExpect(jsonPath("$.data.results[0].primaryCity").value("New York"));
    }

    @Test
    void searchWorkersShouldFilterByCategoryId() throws Exception {
        mockMvc.perform(get("/api/v1/search/workers")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andExpect(jsonPath("$.data.filters.categoryId").value(category.getId().toString()));
    }

    @Test
    void searchWorkersShouldReturnEmptyResultsWhenCityDoesNotMatch() throws Exception {
        mockMvc.perform(get("/api/v1/search/workers")
                        .param("city", "Chicago")
                        .param("verified", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void searchWorkersShouldSortByPriceAscending() throws Exception {
        when(availabilityService.isAvailableOnDate(any(), any())).thenReturn(true);

        mockMvc.perform(get("/api/v1/search/workers")
                        .param("verified", "false")
                        .param("sort", "price")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(2)))
                .andExpect(jsonPath("$.data.results[0].fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.results[1].fullName").value("Bob Builder"));
    }

    @Test
    void searchWorkersShouldIncludeUnverifiedWorkersWhenVerifiedFilterDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/search/workers")
                        .param("verified", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(2)));
    }

    @Test
    void searchWorkersShouldFilterByAvailableOnDate() throws Exception {
        when(availabilityService.isAvailableOnDate(eq(verifiedWorker.getId()), any())).thenReturn(true);
        when(availabilityService.isAvailableOnDate(eq(unverifiedWorker.getId()), any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/search/workers")
                        .param("verified", "false")
                        .param("availableOn", java.time.LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andExpect(jsonPath("$.data.results[0].fullName").value("Bob Builder"));
    }
}
