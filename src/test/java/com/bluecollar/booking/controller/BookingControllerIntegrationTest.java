package com.bluecollar.booking.controller;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class BookingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.bluecollar.availability.service.AvailabilityService availabilityService;

    private UserAccount customerUserAccount;
    private UserAccount workerUserAccount;
    private Customer customer;
    private Worker worker;
    private Category category;
    private Address address;

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken customerAuth;
    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken workerAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        bookingRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        customerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust@example.com")
                .phoneNumber("+1234567890")
                .passwordHash("password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        workerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("work@example.com")
                .phoneNumber("+1111111111")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customerUserAccount)
                .firstName("John")
                .lastName("Doe")
                .active(true)
                .build());

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
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

        address = addressRepository.saveAndFlush(Address.builder()
                .customer(customer)
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("123 Main St")
                .city("New York")
                .state("NY")
                .pincode("100001")
                .isDefault(true)
                .active(true)
                .build());

        com.bluecollar.common.security.AuthenticatedUser authenticatedCust = new com.bluecollar.common.security.AuthenticatedUser(
                customerUserAccount.getId(),
                customerUserAccount.getEmail(),
                UserRole.CUSTOMER
        );
        customerAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authenticatedCust,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        com.bluecollar.common.security.AuthenticatedUser authenticatedWork = new com.bluecollar.common.security.AuthenticatedUser(
                workerUserAccount.getId(),
                workerUserAccount.getEmail(),
                UserRole.WORKER
        );
        workerAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authenticatedWork,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WORKER"))
        );
    }

    @Test
    void createBookingShouldReturnCreated() throws Exception {
        org.mockito.Mockito.when(availabilityService.isAvailableOnDate(
                org.mockito.Mockito.eq(worker.getId()),
                org.mockito.Mockito.any(LocalDate.class))
        ).thenReturn(true);
        String payload = """
                {
                  "workerId": "%s",
                  "categoryId": "%s",
                  "addressId": "%s",
                  "scheduledDate": "%s",
                  "timeSlot": "10:00-12:00",
                  "description": "Fix my pipes"
                }
                """.formatted(worker.getId(), category.getId(), address.getId(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.description").value("Fix my pipes"));
    }

    @Test
    void acceptBookingShouldReturnOk() throws Exception {
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.PENDING)
                .scheduledDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-12:00")
                .description("Fix leak")
                .quotedAmount(BigDecimal.valueOf(100.00))
                .addressLine1("123 Main St")
                .addressCity("New York")
                .addressState("NY")
                .addressPincode("100001")
                .build());

        mockMvc.perform(put("/api/v1/bookings/{id}/accept", booking.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }
}
