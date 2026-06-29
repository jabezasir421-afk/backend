package com.bluecollar.auth.controller;

import com.bluecollar.auth.repository.RefreshTokenRepository;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private Category category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        testDbCleanup.cleanAuthArtifacts();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        testDbCleanup.cleanAuthArtifacts();
    }

    @Test
    void registerCustomerShouldCreateCustomerAndReturnAuthResponse() throws Exception {
        String payload = """
                {
                  "email": "customer@example.com",
                  "phoneNumber": "9876543210",
                  "password": "Password@1",
                  "firstName": "John",
                  "lastName": "Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer registered successfully"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.profileId").exists());
    }

    @Test
    void registerWorkerShouldCreateWorkerAndReturnAuthResponse() throws Exception {
        String payload = """
                {
                  "email": "worker@example.com",
                  "phoneNumber": "9876543211",
                  "password": "Password@1",
                  "firstName": "Bob",
                  "lastName": "Builder",
                  "hourlyRate": 50.00,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/auth/register/worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker registered successfully"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.role").value("WORKER"))
                .andExpect(jsonPath("$.data.profileId").exists());
    }

    @Test
    void loginShouldReturnAuthResponseWhenCredentialsAreValid() throws Exception {
        registerCustomer("login@example.com", "9876543220");

        String payload = """
                {
                  "identifier": "login@example.com",
                  "password": "Password@1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    void loginShouldReturnUnauthorizedWhenPasswordIsWrong() throws Exception {
        registerCustomer("wrongpass@example.com", "9876543221");

        String payload = """
                {
                  "identifier": "wrongpass@example.com",
                  "password": "WrongPass1!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email/phone or password"));
    }

    @Test
    void refreshShouldReturnNewTokensWhenRefreshTokenIsValid() throws Exception {
        MvcResult registerResult = registerCustomer("refresh@example.com", "9876543222");
        String refreshToken = extractJsonPath(registerResult, "$.data.refreshToken");

        String payload = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    void logoutShouldRevokeRefreshTokenWhenAuthenticated() throws Exception {
        MvcResult registerResult = registerCustomer("logout@example.com", "9876543223");
        String accessToken = extractJsonPath(registerResult, "$.data.accessToken");
        String refreshToken = extractJsonPath(registerResult, "$.data.refreshToken");

        String payload = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logout successful"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserShouldReturnCurrentUserWhenAuthenticated() throws Exception {
        MvcResult registerResult = registerCustomer("me@example.com", "9876543224");
        String accessToken = extractJsonPath(registerResult, "$.data.accessToken");
        String profileId = extractJsonPath(registerResult, "$.data.profileId");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Current user fetched successfully"))
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("9876543224"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.customerId").value(profileId));
    }

    @Test
    void registerCustomerShouldReturnValidationErrorsForInvalidPayload() throws Exception {
        String payload = """
                {
                  "email": "not-an-email",
                  "phoneNumber": "12345",
                  "password": "weak",
                  "firstName": "",
                  "lastName": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(5)));
    }

    @Test
    void getCurrentUserShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerCustomerShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        registerCustomer("duplicate@example.com", "9876543225");

        String payload = """
                {
                  "email": "duplicate@example.com",
                  "phoneNumber": "9876543226",
                  "password": "Password@1",
                  "firstName": "Jane",
                  "lastName": "Smith"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    private MvcResult registerCustomer(String email, String phoneNumber) throws Exception {
        String payload = """
                {
                  "email": "%s",
                  "phoneNumber": "%s",
                  "password": "Password@1",
                  "firstName": "John",
                  "lastName": "Doe"
                }
                """.formatted(email, phoneNumber);

        return mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractJsonPath(MvcResult result, String jsonPath) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }
}
