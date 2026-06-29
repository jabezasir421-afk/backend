package com.bluecollar.worker.controller;

import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class WorkerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorkerShouldCreateWorkerAndReturnApiResponse() throws Exception {
        String payload = """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "phoneNumber": "+1234567890",
                  "email": "john.doe@example.com",
                  "gender": "MALE",
                  "dateOfBirth": "1990-01-01",
                  "experienceYears": 5,
                  "bio": "Experienced plumber",
                  "hourlyRate": 50.00,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/workers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker created successfully"))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andExpect(jsonPath("$.data.phoneNumber").value("+1234567890"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorkerShouldReturnValidationErrorsForMissingRequiredFields() throws Exception {
        String payload = """
                {
                  "firstName": "",
                  "lastName": "",
                  "phoneNumber": ""
                }
                """;

        mockMvc.perform(post("/api/v1/workers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorkerShouldReturnConflictWhenPhoneNumberAlreadyExists() throws Exception {
        workerRepository.saveAndFlush(buildWorker("+1234567890", "existing@example.com"));

        String payload = """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "phoneNumber": "+1234567890",
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/workers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Worker with phone number '+1234567890' already exists"));
    }

    @Test
    void createWorkerShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        String payload = """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "phoneNumber": "+1234567890",
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/workers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllWorkersShouldReturnPaginatedWorkers() throws Exception {
        workerRepository.saveAndFlush(buildWorker("+1111111111", "worker1@example.com"));
        workerRepository.saveAndFlush(buildWorker("+2222222222", "worker2@example.com"));

        mockMvc.perform(get("/api/v1/workers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Workers fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getAllWorkersShouldReturnEmptyPageWhenNoWorkersExist() throws Exception {
        mockMvc.perform(get("/api/v1/workers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void getWorkerByIdShouldReturnWorker() throws Exception {
        Worker worker = workerRepository.saveAndFlush(buildWorker("+1234567890", "john.doe@example.com"));

        mockMvc.perform(get("/api/v1/workers/{id}", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(worker.getId().toString()))
                .andExpect(jsonPath("$.data.phoneNumber").value("+1234567890"));
    }

    @Test
    void getWorkerByIdShouldReturnNotFoundForMissingWorker() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/workers/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Worker with id '%s' was not found".formatted(missingId)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateWorkerShouldUpdateWorkerAndReturnApiResponse() throws Exception {
        Worker worker = workerRepository.saveAndFlush(buildWorker("+1234567890", "john.doe@example.com"));

        String payload = """
                {
                  "firstName": "Jane",
                  "lastName": "Smith",
                  "phoneNumber": "+0987654321",
                  "email": "jane.smith@example.com",
                  "gender": "FEMALE",
                  "dateOfBirth": "1992-06-15",
                  "experienceYears": 3,
                  "bio": "Expert electrician",
                  "hourlyRate": 60.00,
                  "available": true,
                  "verified": true,
                  "active": true,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/workers/{id}", worker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker updated successfully"))
                .andExpect(jsonPath("$.data.firstName").value("Jane"))
                .andExpect(jsonPath("$.data.phoneNumber").value("+0987654321"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateWorkerShouldReturnNotFoundForMissingWorker() throws Exception {
        UUID missingId = UUID.randomUUID();
        String payload = """
                {
                  "firstName": "Jane",
                  "lastName": "Smith",
                  "phoneNumber": "+0987654321",
                  "available": true,
                  "verified": false,
                  "active": true,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/workers/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Worker with id '%s' was not found".formatted(missingId)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWorkerShouldDeleteExistingWorker() throws Exception {
        Worker worker = workerRepository.saveAndFlush(buildWorker("+1234567890", "john.doe@example.com"));

        mockMvc.perform(delete("/api/v1/workers/{id}", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Worker deleted successfully"));

        assertFalse(workerRepository.findById(worker.getId()).isPresent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWorkerShouldReturnNotFoundForMissingWorker() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/workers/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Worker with id '%s' was not found".formatted(missingId)));
    }

    @Test
    void deleteWorkerShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        Worker worker = workerRepository.saveAndFlush(buildWorker("+1234567890", "john.doe@example.com"));

        mockMvc.perform(delete("/api/v1/workers/{id}", worker.getId()))
                .andExpect(status().isForbidden());
    }

    private Worker buildWorker(String phoneNumber, String email) {
        return Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber(phoneNumber)
                .email(email)
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Experienced plumber")
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .build();
    }
}
