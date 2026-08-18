package com.bluecollar.address.controller;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AddressControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount savedUserAccount;
    private Customer savedCustomer;
    private UsernamePasswordAuthenticationToken customerAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        notificationRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        userAccountRepository.deleteAll();

        savedUserAccount = userAccountRepository.saveAndFlush(
                buildUserAccount("test.customer@example.com", "+919876543210", UserRole.CUSTOMER)
        );
        savedCustomer = customerRepository.saveAndFlush(
                buildCustomer(savedUserAccount, "John", "Doe")
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                savedUserAccount.getId(),
                savedUserAccount.getEmail(),
                UserRole.CUSTOMER
        );
        customerAuth = new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    // ─── POST /api/v1/customers/me/addresses ──────────────────────────────────

    @Test
    void createAddressShouldCreateAddressAndReturn21Created() throws Exception {
        String payload = """
                {
                  "label": "Home",
                  "addressType": "HOME",
                  "line1": "123 Main St",
                  "line2": "Apt 4B",
                  "landmark": "Near Park",
                  "city": "Mumbai",
                  "state": "Maharashtra",
                  "pincode": "400001",
                  "latitude": 19.0760,
                  "longitude": 72.8777,
                  "isDefault": true
                }
                """;

        mockMvc.perform(post("/api/v1/customers/me/addresses")
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address created successfully"))
                .andExpect(jsonPath("$.data.label").value("Home"))
                .andExpect(jsonPath("$.data.line1").value("123 Main St"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void createAddressShouldReturn400BadRequestWhenValidationFails() throws Exception {
        String payload = """
                {
                  "label": "",
                  "addressType": "HOME",
                  "line1": "",
                  "city": "",
                  "state": "Maharashtra",
                  "pincode": "invalid-pincode",
                  "latitude": 95.0,
                  "longitude": 200.0,
                  "isDefault": false
                }
                """;

        mockMvc.perform(post("/api/v1/customers/me/addresses")
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(6)));
    }

    @Test
    void createAddressShouldReturn409ConflictWhenMaxAddressLimitReached() throws Exception {
        // Arrange
        for (int i = 0; i < 10; i++) {
            addressRepository.save(buildAddress(savedCustomer, "Addr " + i, AddressType.HOME, "L1", "400001", false));
        }
        addressRepository.flush();

        String payload = """
                {
                  "label": "New Addr",
                  "addressType": "HOME",
                  "line1": "Line 1",
                  "city": "Mumbai",
                  "state": "Maharashtra",
                  "pincode": "400001",
                  "isDefault": false
                }
                """;

        mockMvc.perform(post("/api/v1/customers/me/addresses")
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Maximum of 10 active addresses allowed per customer"));
    }

    // ─── GET /api/v1/customers/me/addresses ───────────────────────────────────

    @Test
    void getMyAddressesShouldReturnAllActiveAddressesForCustomer() throws Exception {
        addressRepository.saveAndFlush(buildAddress(savedCustomer, "Home", AddressType.HOME, "L1", "400001", true));
        addressRepository.saveAndFlush(buildAddress(savedCustomer, "Work", AddressType.WORK, "L2", "400002", false));

        mockMvc.perform(get("/api/v1/customers/me/addresses")
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Addresses fetched successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ─── GET /api/v1/customers/me/addresses/{id} ───────────────────────────────

    @Test
    void getMyAddressByIdShouldReturnCorrectAddress() throws Exception {
        Address saved = addressRepository.saveAndFlush(
                buildAddress(savedCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );

        mockMvc.perform(get("/api/v1/customers/me/addresses/{id}", saved.getId())
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.label").value("Home"));
    }

    @Test
    void getMyAddressByIdShouldReturn404NotFoundWhenNotOwned() throws Exception {
        UserAccount secondUser = userAccountRepository.saveAndFlush(
                buildUserAccount("other.customer@example.com", "+919876543211", UserRole.CUSTOMER)
        );
        Customer otherCustomer = customerRepository.saveAndFlush(
                buildCustomer(secondUser, "Jane", "Doe")
        );
        Address otherAddress = addressRepository.saveAndFlush(
                buildAddress(otherCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );

        mockMvc.perform(get("/api/v1/customers/me/addresses/{id}", otherAddress.getId())
                        .with(authentication(customerAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address with id '" + otherAddress.getId() + "' was not found"));
    }

    // ─── PUT /api/v1/customers/me/addresses/{id} ───────────────────────────────

    @Test
    void updateAddressShouldModifyExistingAddressAndReturn200Ok() throws Exception {
        Address saved = addressRepository.saveAndFlush(
                buildAddress(savedCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );

        String payload = """
                {
                  "label": "Office",
                  "addressType": "WORK",
                  "line1": "456 Office Rd",
                  "line2": "Suite 10",
                  "landmark": "Near Mall",
                  "city": "Mumbai",
                  "state": "Maharashtra",
                  "pincode": "400002",
                  "latitude": 19.0800,
                  "longitude": 72.8800,
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/customers/me/addresses/{id}", saved.getId())
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address updated successfully"))
                .andExpect(jsonPath("$.data.label").value("Office"))
                .andExpect(jsonPath("$.data.addressType").value("WORK"));
    }

    @Test
    void updateAddressShouldReturn404NotFoundWhenNotOwned() throws Exception {
        UserAccount secondUser = userAccountRepository.saveAndFlush(
                buildUserAccount("other.customer@example.com", "+919876543211", UserRole.CUSTOMER)
        );
        Customer otherCustomer = customerRepository.saveAndFlush(
                buildCustomer(secondUser, "Jane", "Doe")
        );
        Address otherAddress = addressRepository.saveAndFlush(
                buildAddress(otherCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );

        String payload = """
                {
                  "label": "Office",
                  "addressType": "WORK",
                  "line1": "456 Office Rd",
                  "city": "Mumbai",
                  "state": "Maharashtra",
                  "pincode": "400002",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/customers/me/addresses/{id}", otherAddress.getId())
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/customers/me/addresses/{id} ────────────────────────────

    @Test
    void deleteAddressShouldDeactivateAddressAndReturn200Ok() throws Exception {
        Address saved = addressRepository.saveAndFlush(
                buildAddress(savedCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );

        mockMvc.perform(delete("/api/v1/customers/me/addresses/{id}", saved.getId())
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Address deleted successfully"));

        Address result = addressRepository.findById(saved.getId()).orElseThrow();
        assertFalse(result.getActive());
        assertFalse(result.getIsDefault());
    }

    // ─── PUT /api/v1/customers/me/addresses/{id}/default ────────────────────────

    @Test
    void setDefaultAddressShouldUpdateDefaultFlagAndReturn200Ok() throws Exception {
        Address oldDefault = addressRepository.saveAndFlush(
                buildAddress(savedCustomer, "Home", AddressType.HOME, "L1", "400001", true)
        );
        Address newDefault = addressRepository.saveAndFlush(
                buildAddress(savedCustomer, "Work", AddressType.WORK, "L2", "400002", false)
        );

        mockMvc.perform(put("/api/v1/customers/me/addresses/{id}/default", newDefault.getId())
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Default address updated successfully"));

        Address oldResult = addressRepository.findById(oldDefault.getId()).orElseThrow();
        Address newResult = addressRepository.findById(newDefault.getId()).orElseThrow();

        assertFalse(oldResult.getIsDefault());
        assertTrue(newResult.getIsDefault());
    }

    // ─── SECURITY / AUTHORIZATION TESTS ──────────────────────────────────────

    @Test
    void endpointsShouldReturn403ForbiddenWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me/addresses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void endpointsShouldReturn403ForbiddenWhenRoleIsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me/addresses"))
                .andExpect(status().isForbidden());
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private UserAccount buildUserAccount(String email, String phone, UserRole role) {
        return UserAccount.builder()
                .email(email)
                .phoneNumber(phone)
                .passwordHash("hashed_password")
                .role(role)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
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

    private Address buildAddress(Customer customer, String label, AddressType type, String line1, String pincode, boolean isDefault) {
        return Address.builder()
                .customer(customer)
                .label(label)
                .addressType(type)
                .line1(line1)
                .city("Mumbai")
                .state("Maharashtra")
                .pincode(pincode)
                .isDefault(isDefault)
                .active(true)
                .build();
    }
}
