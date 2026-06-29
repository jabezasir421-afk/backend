package com.bluecollar.booking.repository;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingRepositoryTest {

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

    private Customer customer;
    private Worker worker;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        UserAccount customerUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust@example.com")
                .phoneNumber("+1234567890")
                .passwordHash("password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customerUser)
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
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private Booking saveBooking(BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(status)
                .scheduledDate(LocalDate.now())
                .timeSlot("10:00-12:00")
                .description("Fix leak")
                .quotedAmount(BigDecimal.valueOf(100.00))
                .addressLine1("123 Main St")
                .addressCity("New York")
                .addressState("NY")
                .addressPincode("100001")
                .build());
    }

    // ─── findByCustomerId ──────────────────────────────────────────────────────

    @Test
    void findByCustomerIdShouldReturnPageOfBookings() {
        saveBooking(BookingStatus.PENDING);

        Page<Booking> result = bookingRepository.findByCustomerId(customer.getId(), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByCustomerIdShouldReturnEmptyForUnknownCustomer() {
        saveBooking(BookingStatus.PENDING);

        Page<Booking> result = bookingRepository.findByCustomerId(java.util.UUID.randomUUID(), PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    // ─── findByWorkerId ────────────────────────────────────────────────────────

    @Test
    void findByWorkerIdShouldReturnPageOfBookings() {
        saveBooking(BookingStatus.PENDING);

        Page<Booking> result = bookingRepository.findByWorkerId(worker.getId(), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ─── findByCustomerIdAndStatus ─────────────────────────────────────────────

    @Test
    void findByCustomerIdAndStatusShouldFilterByStatus() {
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.COMPLETED);

        Page<Booking> result = bookingRepository.findByCustomerIdAndStatus(
                customer.getId(), BookingStatus.PENDING, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ─── findByWorkerIdAndStatus ───────────────────────────────────────────────

    @Test
    void findByWorkerIdAndStatusShouldFilterByStatus() {
        saveBooking(BookingStatus.ACCEPTED);
        saveBooking(BookingStatus.COMPLETED);

        Page<Booking> result = bookingRepository.findByWorkerIdAndStatus(
                worker.getId(), BookingStatus.ACCEPTED, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ─── countByStatus ─────────────────────────────────────────────────────────

    @Test
    void countByStatusShouldReturnCorrectCount() {
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.COMPLETED);

        assertEquals(2, bookingRepository.countByStatus(BookingStatus.PENDING));
        assertEquals(1, bookingRepository.countByStatus(BookingStatus.COMPLETED));
    }

    // ─── countCompleted ────────────────────────────────────────────────────────

    @Test
    void countCompletedShouldReturnCountOfCompletedBookings() {
        saveBooking(BookingStatus.COMPLETED);
        saveBooking(BookingStatus.PENDING);

        assertEquals(1, bookingRepository.countCompleted());
    }

    // ─── countByStatusIn ───────────────────────────────────────────────────────

    @Test
    void countByStatusInShouldReturnCombinedCount() {
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.ACCEPTED);
        saveBooking(BookingStatus.COMPLETED);

        long count = bookingRepository.countByStatusIn(
                List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED));

        assertEquals(2, count);
    }

    // ─── findAllWithFilters ────────────────────────────────────────────────────

    @Test
    void findAllWithFiltersShouldReturnAllWhenNoFilters() {
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.COMPLETED);

        Page<Booking> result = bookingRepository.findAllWithFilters(
                null, null, null, null, null, null, PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
    }

    @Test
    void findAllWithFiltersShouldFilterByStatus() {
        saveBooking(BookingStatus.PENDING);
        saveBooking(BookingStatus.COMPLETED);

        Page<Booking> result = bookingRepository.findAllWithFilters(
                BookingStatus.PENDING, null, null, null, null, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ─── sumFinalAmountByStatus ────────────────────────────────────────────────

    @Test
    void sumFinalAmountByStatusShouldReturnSumOfCompletedBookings() {
        Booking b1 = saveBooking(BookingStatus.COMPLETED);
        b1.setFinalAmount(BigDecimal.valueOf(100.00));
        bookingRepository.saveAndFlush(b1);

        Booking b2 = saveBooking(BookingStatus.COMPLETED);
        b2.setFinalAmount(BigDecimal.valueOf(200.00));
        bookingRepository.saveAndFlush(b2);

        BigDecimal sum = bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED);

        assertTrue(sum.compareTo(BigDecimal.valueOf(300.00)) == 0);
    }

    @Test
    void sumFinalAmountByStatusShouldReturnZeroWhenNoBookings() {
        BigDecimal sum = bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED);

        assertTrue(sum.compareTo(BigDecimal.ZERO) == 0);
    }
}
