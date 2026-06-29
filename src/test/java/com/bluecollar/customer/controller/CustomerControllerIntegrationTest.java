package com.bluecollar.customer.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CustomerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount savedUserAccount;
    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        customerRepository.deleteAll();
        userAccountRepository.deleteAll();

        savedUserAccount = userAccountRepository.saveAndFlush(
                buildUserAccount("john.doe@example.com", "+919876543210", UserRole.CUSTOMER)
        );
        savedCustomer = customerRepository.saveAndFlush(
                buildCustomer(savedUserAccount, "John", "Doe")
        );
    }

    // ─── GET /api/v1/customers/me ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getMyProfileShouldReturnForbiddenForCustomerRoleWithoutRealJwt() throws Exception {
        // The endpoint requires a real JWT-derived AuthenticatedUser in the security context.
        // @WithMockUser injects a Spring UserDetails principal (not AuthenticatedUser), so
        // SecurityUtils.getCurrentUser() throws UnauthorizedException → 403 Forbidden.
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfileShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyProfileShouldReturnForbiddenForAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/v1/customers/me ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateMyProfileShouldReturnForbiddenForCustomerRoleWithoutRealJwt() throws Exception {
        String payload = "{\"firstName\":\"Jane\",\"lastName\":\"Smith\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateMyProfileShouldReturnValidationErrorsForBlankFirstName() throws Exception {
        String payload = "{\"firstName\":\"\",\"lastName\":\"Smith\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateMyProfileShouldReturnValidationErrorsForBlankLastName() throws Exception {
        String payload = "{\"firstName\":\"Jane\",\"lastName\":\"\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lastName"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateMyProfileShouldReturnValidationErrorsWhenBothNamesAreBlank() throws Exception {
        String payload = "{\"firstName\":\"\",\"lastName\":\"\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
    }

    @Test
    void updateMyProfileShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        String payload = "{\"firstName\":\"Jane\",\"lastName\":\"Smith\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateMyProfileShouldReturnForbiddenForAdminRole() throws Exception {
        String payload = "{\"firstName\":\"Jane\",\"lastName\":\"Smith\"}";

        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/customers ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllCustomersShouldReturnPaginatedCustomersForAdmin() throws Exception {
        UserAccount second = userAccountRepository.saveAndFlush(
                buildUserAccount("jane.smith@example.com", "+919876543211", UserRole.CUSTOMER)
        );
        customerRepository.saveAndFlush(buildCustomer(second, "Jane", "Smith"));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customers fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllCustomersShouldReturnEmptyPageWhenNoCustomersExist() throws Exception {
        customerRepository.deleteAll();

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void getAllCustomersShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getAllCustomersShouldReturnForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/customers/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCustomerByIdShouldReturnCustomerForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", savedCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer fetched successfully"))
                .andExpect(jsonPath("$.data.id").value(savedCustomer.getId().toString()))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCustomerByIdShouldReturnNotFoundForMissingCustomer() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/customers/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Customer with id '%s' was not found".formatted(missingId)
                ));
    }

    @Test
    void getCustomerByIdShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", savedCustomer.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getCustomerByIdShouldReturnForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", savedCustomer.getId()))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/v1/customers/{id}/deactivate ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateCustomerShouldDeactivateExistingCustomer() throws Exception {
        mockMvc.perform(put("/api/v1/customers/{id}/deactivate", savedCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer deactivated successfully"));

        Customer deactivated = customerRepository.findById(savedCustomer.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(deactivated.getActive());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateCustomerShouldReturnNotFoundForMissingCustomer() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/customers/{id}/deactivate", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Customer with id '%s' was not found".formatted(missingId)
                ));
    }

    @Test
    void deactivateCustomerShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/customers/{id}/deactivate", savedCustomer.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deactivateCustomerShouldReturnForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(put("/api/v1/customers/{id}/deactivate", savedCustomer.getId()))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private UserAccount buildUserAccount(String email, String phone, UserRole role) {
        return UserAccount.builder()
                .email(email)
                .phoneNumber(phone)
                .passwordHash("hashed_password")
                .role(role)
                .active(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }

    private Customer buildCustomer(UserAccount userAccount, String firstName, String lastName) {
        return Customer.builder()
                .userAccount(userAccount)
                .firstName(firstName)
                .lastName(lastName)
                .active(true)
                .build();
    }
}
