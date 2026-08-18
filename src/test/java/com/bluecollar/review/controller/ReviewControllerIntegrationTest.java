package com.bluecollar.review.controller;

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
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;
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

    private UserAccount customerUserAccount;
    private UserAccount adminUserAccount;
    private Customer customer;
    private Worker worker;
    private Category category;
    private Address address;
    private Booking booking;
    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken customerAuth;
    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        // Create user accounts first to avoid FK constraints
        customerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("john.doe@example.com")
                .phoneNumber("+1234567890")
                .passwordHash("password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        adminUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("admin@example.com")
                .phoneNumber("+1111111111")
                .passwordHash("password")
                .role(UserRole.ADMIN)
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
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+0987654321")
                .email("worker@example.com")
                .category(category)
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

        booking = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now())
                .timeSlot("10:00 - 12:00")
                .description("Fix leak")
                .quotedAmount(BigDecimal.valueOf(100.00))
                .addressLine1("123 Main St")
                .addressCity("New York")
                .addressState("NY")
                .addressPincode("100001")
                .completedAt(Instant.now())
                .build());

        com.bluecollar.common.security.AuthenticatedUser authenticatedUser = new com.bluecollar.common.security.AuthenticatedUser(
                customerUserAccount.getId(),
                customerUserAccount.getEmail(),
                UserRole.CUSTOMER
        );
        customerAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        com.bluecollar.common.security.AuthenticatedUser authenticatedAdmin = new com.bluecollar.common.security.AuthenticatedUser(
                adminUserAccount.getId(),
                adminUserAccount.getEmail(),
                UserRole.ADMIN
        );
        adminAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authenticatedAdmin,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    void createReviewShouldCreateReview() throws Exception {
        String payload = """
                {
                  "bookingId": "%s",
                  "rating": 5,
                  "comment": "Perfect work!"
                }
                """.formatted(booking.getId());

        mockMvc.perform(post("/api/v1/reviews")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.comment").value("Perfect work!"))
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void deactivateReviewShouldDeactivate() throws Exception {
        Review review = reviewRepository.saveAndFlush(Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        mockMvc.perform(put("/api/v1/reviews/{id}/deactivate", review.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void approveReviewShouldApprovePendingReview() throws Exception {
        Review review = reviewRepository.saveAndFlush(Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.PENDING)
                .build());

        String payload = "{\"notes\":\"Approved by admin\"}";

        mockMvc.perform(put("/api/v1/admin/reviews/{id}/approve", review.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(adminAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));
    }

    @Test
    void getWorkerReviewsShouldFetchSuccessfully() throws Exception {
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        mockMvc.perform(get("/api/v1/reviews/worker/{workerId}", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }
}
