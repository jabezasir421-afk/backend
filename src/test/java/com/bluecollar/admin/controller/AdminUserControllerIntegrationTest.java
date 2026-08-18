package com.bluecollar.admin.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.common.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminUserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private UserAccount inactiveUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        testDbCleanup.cleanCoreDomain();

        inactiveUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("inactive.user@example.com")
                .phoneNumber("+919876543201")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(false)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("active.user@example.com")
                .phoneNumber("+919876543202")
                .passwordHash("hashed_password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsersShouldReturnPaginatedUsersForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Users fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserShouldReturnUserByIdForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", inactiveUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User fetched successfully"))
                .andExpect(jsonPath("$.data.id").value(inactiveUser.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("inactive.user@example.com"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateUserShouldActivateInactiveUser() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/activate", inactiveUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User activated"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUserShouldDeactivateActiveUser() throws Exception {
        UserAccount activeUser = userAccountRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/v1/admin/users/{id}/deactivate", activeUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deactivated"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void changeRoleShouldUpdateUserRole() throws Exception {
        AuthenticatedUser adminUser = new AuthenticatedUser(
                UUID.randomUUID(),
                "admin@example.com",
                UserRole.ADMIN
        );
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                adminUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        mockMvc.perform(put("/api/v1/admin/users/{id}/role", inactiveUser.getId())
                        .param("role", "WORKER")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Role updated"))
                .andExpect(jsonPath("$.data.role").value("WORKER"));
    }

    @Test
    void listUsersShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void listUsersShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }
}
