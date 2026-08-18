package com.bluecollar.review.repository;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReviewRepositoryTest {

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
    private com.bluecollar.auth.repository.UserAccountRepository userAccountRepository;

    private Customer customer;
    private Worker worker;
    private Booking booking1;
    private Booking booking2;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        com.bluecollar.auth.entity.UserAccount userAccount = userAccountRepository.saveAndFlush(
                com.bluecollar.auth.entity.UserAccount.builder()
                        .email("customer@example.com")
                        .phoneNumber("+1234567890")
                        .passwordHash("password")
                        .role(com.bluecollar.auth.entity.UserRole.CUSTOMER)
                        .active(true)
                        .emailVerified(true)
                        .phoneVerified(true)
                        .build());

        customer = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(userAccount)
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

        booking1 = bookingRepository.saveAndFlush(Booking.builder()
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
                .build());

        booking2 = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now())
                .timeSlot("14:00 - 16:00")
                .description("Fix pipe")
                .quotedAmount(BigDecimal.valueOf(150.00))
                .addressLine1("123 Main St")
                .addressCity("New York")
                .addressState("NY")
                .addressPincode("100001")
                .build());
    }

    @Test
    void existsByBookingIdShouldReturnTrueWhenReviewExists() {
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking1)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        boolean exists = reviewRepository.existsByBookingId(booking1.getId());
        assertTrue(exists);
    }

    @Test
    void existsByBookingIdShouldReturnFalseWhenReviewDoesNotExist() {
        boolean exists = reviewRepository.existsByBookingId(UUID.randomUUID());
        assertFalse(exists);
    }

    @Test
    void findByWorkerIdAndActiveTrueAndModerationStatusShouldReturnPageOfReviews() {
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking1)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking2)
                .customer(customer)
                .worker(worker)
                .rating((short) 4)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        Page<Review> result = reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(
                worker.getId(), ModerationStatus.APPROVED, PageRequest.of(0, 10, Sort.by("rating").descending()));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals((short) 5, result.getContent().get(0).getRating());
    }

    @Test
    void findByIdAndActiveTrueShouldReturnActiveReview() {
        Review review = reviewRepository.saveAndFlush(Review.builder()
                .booking(booking1)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        Optional<Review> found = reviewRepository.findByIdAndActiveTrue(review.getId());
        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndActiveTrueShouldReturnEmptyForInactiveReview() {
        Review review = reviewRepository.saveAndFlush(Review.builder()
                .booking(booking1)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .active(false)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        Optional<Review> found = reviewRepository.findByIdAndActiveTrue(review.getId());
        assertFalse(found.isPresent());
    }
}
