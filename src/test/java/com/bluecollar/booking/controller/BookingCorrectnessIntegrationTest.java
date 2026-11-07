package com.bluecollar.booking.controller;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BookingCorrectnessIntegrationTest {

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
    private WorkerWorkingHoursRepository workingHoursRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount customer1User;
    private UserAccount customer2User;
    private UserAccount workerUser;
    private Customer customer1;
    private Customer customer2;
    private Worker worker;
    private Category category;
    private Address address1;
    private Address address2;

    private UsernamePasswordAuthenticationToken customer1Auth;
    private UsernamePasswordAuthenticationToken customer2Auth;

    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        bookingRepository.deleteAll();
        workingHoursRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        // Target booking date is 3 days in the future
        bookingDate = LocalDate.now().plusDays(3);

        customer1User = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust1@example.com")
                .phoneNumber("+1234567891")
                .passwordHash("password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer2User = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust2@example.com")
                .phoneNumber("+1234567892")
                .passwordHash("password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        workerUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker@example.com")
                .phoneNumber("+1234567893")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer1 = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customer1User)
                .firstName("Alice")
                .lastName("Smith")
                .active(true)
                .build());

        customer2 = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customer2User)
                .firstName("Bob")
                .lastName("Jones")
                .active(true)
                .build());

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe and leak services")
                .active(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(workerUser)
                .firstName("Charlie")
                .lastName("Plumber")
                .phoneNumber("+1234567893")
                .email("worker@example.com")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(60.00))
                .active(true)
                .verified(true)
                .available(true)
                .vacationMode(false)
                .build());

        // Working hours for the day of week (08:00 - 18:00)
        short dayOfWeek = (short) bookingDate.getDayOfWeek().getValue();
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(18, 0))
                .active(true)
                .build());

        address1 = addressRepository.saveAndFlush(Address.builder()
                .customer(customer1)
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("100 Alice Road")
                .city("New York")
                .state("NY")
                .pincode("100001")
                .active(true)
                .build());

        address2 = addressRepository.saveAndFlush(Address.builder()
                .customer(customer2)
                .label("Office")
                .addressType(AddressType.WORK)
                .line1("200 Bob Avenue")
                .city("New York")
                .state("NY")
                .pincode("100002")
                .active(true)
                .build());

        customer1Auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(customer1User.getId(), customer1User.getEmail(), UserRole.CUSTOMER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        customer2Auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(customer2User.getId(), customer2User.getEmail(), UserRole.CUSTOMER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        workingHoursRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    private String buildBookingJson(UUID workerId, UUID categoryId, UUID addressId, LocalDate date, String slot, String desc) {
        return """
                {
                  "workerId": "%s",
                  "categoryId": "%s",
                  "addressId": "%s",
                  "scheduledDate": "%s",
                  "timeSlot": "%s",
                  "description": "%s"
                }
                """.formatted(workerId, categoryId, addressId, date, slot, desc);
    }

    // ─── 1. Adjacent Bookings ───────────────────────────────────────────────────

    @Test
    void adjacentBookingsShouldBeAccepted() throws Exception {
        // Booking 1: 10:00 - 12:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "10:00-12:00", "First booking")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Booking 2: 12:00 - 14:00 (adjacent immediately after)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "12:00-14:00", "Adjacent after")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Booking 3: 08:00 - 10:00 (adjacent immediately before)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "08:00-10:00", "Adjacent before")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(3, bookingRepository.count());
    }

    // ─── 2. Overlapping Bookings ───────────────────────────────────────────────

    @Test
    void overlappingBookingsShouldBeRejected() throws Exception {
        // Base Booking: 10:00 - 12:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "10:00-12:00", "Base booking")))
                .andExpect(status().isCreated());

        // Overlap A: 11:00 - 13:00 (tail overlap)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "11:00-13:00", "Tail overlap")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Overlap B: 09:00 - 11:00 (head overlap)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "09:00-11:00", "Head overlap")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Overlap C: 10:30 - 11:30 (subset)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "10:30-11:30", "Subset overlap")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Overlap D: 09:00 - 13:00 (superset)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "09:00-13:00", "Superset overlap")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Overlap E: exact match 10:00 - 12:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "10:00-12:00", "Exact duplicate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Exactly 1 booking was created
        assertEquals(1, bookingRepository.count());
    }

    // ─── 3. Outside Availability & Ordering ────────────────────────────────────

    @Test
    void bookingOutsideWorkingHoursShouldBeRejected() throws Exception {
        // Working hours are 08:00 - 18:00
        // Slot starting before 08:00 (07:00 - 09:00)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "07:00-09:00", "Early booking")))
                .andExpect(status().isConflict());

        // Slot ending after 18:00 (17:00 - 19:00)
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "17:00-19:00", "Late booking")))
                .andExpect(status().isConflict());
    }

    @Test
    void bookingWithInvalidDurationOrOrderingShouldBeRejected() throws Exception {
        // Start == End: 10:00 - 10:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "10:00-10:00", "Zero duration")))
                .andExpect(status().isConflict());

        // Start > End: 12:00 - 10:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "12:00-10:00", "Inverted start end")))
                .andExpect(status().isConflict());
    }

    // ─── 4. Rescheduling Overlaps & Preservation ───────────────────────────────

    @Test
    void reschedulingShouldPreventOverlapsAndPreserveHistory() throws Exception {
        // Booking 1: 08:00 - 10:00
        MvcResult res1 = mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "08:00-10:00", "Booking 1")))
                .andExpect(status().isCreated())
                .andReturn();
        String b1Id = com.jayway.jsonpath.JsonPath.read(res1.getResponse().getContentAsString(), "$.data.id");

        // Booking 2: 12:00 - 14:00
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer2Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "12:00-14:00", "Booking 2")))
                .andExpect(status().isCreated());

        // Reschedule Booking 1 into slot overlapping Booking 2 (11:00 - 13:00) -> 409 Conflict
        String reschedulePayload = """
                {
                  "scheduledDate": "%s",
                  "timeSlot": "11:00-13:00",
                  "description": "Attempted conflict"
                }
                """.formatted(bookingDate);

        mockMvc.perform(put("/api/v1/bookings/{id}/reschedule", b1Id)
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reschedulePayload))
                .andExpect(status().isConflict());

        // Reschedule Booking 1 into non-overlapping slot (14:00 - 16:00) -> 200 OK
        String validReschedule = """
                {
                  "scheduledDate": "%s",
                  "timeSlot": "14:00-16:00",
                  "description": "Moved to afternoon"
                }
                """.formatted(bookingDate);

        mockMvc.perform(put("/api/v1/bookings/{id}/reschedule", b1Id)
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReschedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeSlot").value("14:00-16:00"));

        // Verify booking in DB has updated times and preserved IDs
        Booking updatedB1 = bookingRepository.findById(UUID.fromString(b1Id)).orElseThrow();
        assertEquals(LocalTime.of(14, 0), updatedB1.getStartTime());
        assertEquals(LocalTime.of(16, 0), updatedB1.getEndTime());
        assertEquals(customer1.getId(), updatedB1.getCustomer().getId());
    }

    // ─── 5. Status Transitions and Cancellation ────────────────────────────────

    @Test
    void cancellationShouldPreserveHistoryAndPreventArbitraryTransitions() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "10:00-12:00", "To cancel")))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");

        // Cancel booking
        String cancelPayload = """
                {
                  "reason": "Change of plans"
                }
                """;
        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId)
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Try to cancel again -> 409 Conflict (invalid transition from CANCELLED to CANCELLED)
        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId)
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isConflict());

        // Try to reschedule cancelled booking -> 409 Conflict
        String reschedulePayload = """
                {
                  "scheduledDate": "%s",
                  "timeSlot": "14:00-16:00"
                }
                """.formatted(bookingDate);
        mockMvc.perform(put("/api/v1/bookings/{id}/reschedule", bookingId)
                        .with(authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reschedulePayload))
                .andExpect(status().isConflict());

        // Booking record is preserved in database
        Booking cancelled = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Change of plans", cancelled.getCancellationReason());
        assertNotNull(cancelled.getCancelledAt());
        assertNotNull(cancelled.getCancelledBy());
    }

    // ─── 6. Concurrent Booking Requests ────────────────────────────────────────

    @Test
    void concurrentBookingRequestsShouldPreventDoubleBooking() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        String payload1 = buildBookingJson(worker.getId(), category.getId(), address1.getId(), bookingDate, "15:00-17:00", "Concurrent 1");
        String payload2 = buildBookingJson(worker.getId(), category.getId(), address2.getId(), bookingDate, "15:00-17:00", "Concurrent 2");

        Callable<Integer> task1 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                            .with(authentication(customer1Auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload1))
                    .andReturn();
            int statusCode = result.getResponse().getStatus();
            if (statusCode == 201) successCount.incrementAndGet();
            if (statusCode == 409) conflictCount.incrementAndGet();
            return statusCode;
        };

        Callable<Integer> task2 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                            .with(authentication(customer2Auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload2))
                    .andReturn();
            int statusCode = result.getResponse().getStatus();
            if (statusCode == 201) successCount.incrementAndGet();
            if (statusCode == 409) conflictCount.incrementAndGet();
            return statusCode;
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release both threads at once

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one must succeed (201) and one must receive conflict (409)
        assertEquals(1, successCount.get(), "Exactly one concurrent request must succeed");
        assertEquals(1, conflictCount.get(), "Concurrent request for occupied slot must receive 409 Conflict");
        assertEquals(1, bookingRepository.count(), "Database must contain exactly 1 booking");
    }
}
