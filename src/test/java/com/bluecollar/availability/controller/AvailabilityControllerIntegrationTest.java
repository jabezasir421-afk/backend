package com.bluecollar.availability.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.availability.repository.WorkerScheduleOverrideRepository;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerWorkingHoursRepository workingHoursRepository;

    @Autowired
    private WorkerScheduleOverrideRepository scheduleOverrideRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UserAccount workerUserAccount;
    private Worker worker;
    private UsernamePasswordAuthenticationToken workerAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        scheduleOverrideRepository.deleteAll();
        workingHoursRepository.deleteAll();
        workerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        workerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("work@example.com")
                .phoneNumber("+1111111111")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-AvailabilityCtrl")
                .description("Pipe services")
                .active(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(workerUserAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+0987654321")
                .email("worker@example.com")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .active(true)
                .verified(true)
                .available(true)
                .build());

        com.bluecollar.common.security.AuthenticatedUser authenticatedWorker = new com.bluecollar.common.security.AuthenticatedUser(
                workerUserAccount.getId(),
                workerUserAccount.getEmail(),
                UserRole.WORKER
        );
        workerAuth = new UsernamePasswordAuthenticationToken(
                authenticatedWorker,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_WORKER"))
        );
    }

    @Test
    void getMyAvailabilityShouldReturnConfigForWorker() throws Exception {
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build());

        mockMvc.perform(get("/api/v1/workers/me/availability")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Availability fetched successfully"))
                .andExpect(jsonPath("$.data.summary.bookable").value(true))
                .andExpect(jsonPath("$.data.summary.workingHours", hasSize(1)));
    }

    @Test
    void updateOnlineStatusShouldReturnUpdatedSummary() throws Exception {
        String payload = """
                {
                  "status": "ONLINE"
                }
                """;

        mockMvc.perform(put("/api/v1/workers/me/availability/status")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Online status updated successfully"))
                .andExpect(jsonPath("$.data.onlineStatus").value("ONLINE"));
    }

    @Test
    void updateWorkingHoursShouldReturnSavedSchedule() throws Exception {
        String payload = """
                {
                  "schedule": [
                    {
                      "dayOfWeek": 1,
                      "startTime": "09:00:00",
                      "endTime": "17:00:00"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/workers/me/availability/working-hours")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Working hours updated successfully"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(1));
    }

    @Test
    void heartbeatShouldRecordOnlineStatus() throws Exception {
        mockMvc.perform(post("/api/v1/workers/me/availability/heartbeat")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Heartbeat recorded successfully"))
                .andExpect(jsonPath("$.data.onlineStatus").value("ONLINE"));
    }

    @Test
    void getPublicAvailabilityShouldReturnSummaryWithoutAuth() throws Exception {
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 2)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .build());

        mockMvc.perform(get("/api/v1/workers/{id}/availability", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Public availability fetched successfully"))
                .andExpect(jsonPath("$.data.bookable").value(true))
                .andExpect(jsonPath("$.data.workingHours", hasSize(1)));
    }

    @Test
    void getPublicSlotsShouldReturnAvailableSlotsWithoutAuth() throws Exception {
        short dayOfWeek = (short) LocalDate.now().plusDays(1).getDayOfWeek().getValue();
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build());
        LocalDate date = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/v1/workers/{id}/availability/slots", worker.getId())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Available slots fetched successfully"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].startTime").value("09:00:00"));
    }

    @Test
    void getMyAvailabilityShouldReturnForbiddenWithoutWorkerAuth() throws Exception {
        mockMvc.perform(get("/api/v1/workers/me/availability"))
                .andExpect(status().isForbidden());
    }
}
