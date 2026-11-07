package com.bluecollar.analytics;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.analytics.dto.AnalyticsOverviewResponse;
import com.bluecollar.analytics.dto.RatingDistributionResponse;
import com.bluecollar.analytics.dto.TopCategoryResponse;
import com.bluecollar.analytics.dto.TopWorkerResponse;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.analytics.service.AnalyticsService;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.dto.UpdateBookableRequest;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.config.CacheConfig;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.search.cache.SearchCacheKeyGenerator;
import com.bluecollar.search.dto.WorkerSearchResponse;
import com.bluecollar.search.service.WorkerSearchService;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsSearchAndCachingIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private WorkerSearchService workerSearchService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AnalyticsDailySnapshotRepository snapshotRepository;

    @Autowired
    private AnalyticsCategoryRankingRepository categoryRankingRepository;

    @Autowired
    private AnalyticsWorkerRankingRepository workerRankingRepository;

    @Autowired
    private WorkerWorkingHoursRepository workingHoursRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private SearchCacheKeyGenerator searchCacheKeyGenerator;

    private Address address;
    private Category activeCategory;
    private Category inactiveCategory;
    private Worker activeWorker1;
    private Worker activeWorker2;
    private Worker inactiveWorker;
    private Customer customer;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache(CacheConfig.WORKER_SEARCH_CACHE);
        if (cache != null) {
            cache.clear();
        }

        categoryRankingRepository.deleteAll();
        workerRankingRepository.deleteAll();
        snapshotRepository.deleteAll();
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        addressRepository.deleteAll();
        workingHoursRepository.deleteAll();
        workerRepository.deleteAll();
        customerRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();

        activeCategory = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-Analytics")
                .description("Active category")
                .active(true)
                .build());

        inactiveCategory = categoryRepository.saveAndFlush(Category.builder()
                .name("Old-Carpentry-Analytics")
                .description("Inactive category")
                .active(false)
                .build());

        UserAccount userAccount1 = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker1@analytics.test")
                .phoneNumber("+1999111222")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        UserAccount userAccount2 = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker2@analytics.test")
                .phoneNumber("+1999111333")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        UserAccount userAccount3 = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker3@analytics.test")
                .phoneNumber("+1999111444")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(false)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        UserAccount customerAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("customer@analytics.test")
                .phoneNumber("+1999111555")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customerAccount)
                .firstName("Alice")
                .lastName("Customer")
                .active(true)
                .build());

        address = addressRepository.saveAndFlush(Address.builder()
                .customer(customer)
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("123 Main St")
                .city("Austin")
                .state("TX")
                .pincode("78701")
                .isDefault(true)
                .active(true)
                .build());

        activeWorker1 = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount1)
                .firstName("John")
                .lastName("ActiveOne")
                .email("worker1@analytics.test")
                .phoneNumber("+1999111222")
                .primaryCity("Austin")
                .category(activeCategory)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .averageRating(BigDecimal.valueOf(5.0))
                .reviewCount(1)
                .active(true)
                .verified(false) // active but unverified
                .available(true)
                .build());

        activeWorker2 = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount2)
                .firstName("Jack")
                .lastName("ActiveTwo")
                .email("worker2@analytics.test")
                .phoneNumber("+1999111333")
                .primaryCity("Austin")
                .category(activeCategory)
                .hourlyRate(BigDecimal.valueOf(60.00))
                .averageRating(BigDecimal.valueOf(4.0))
                .reviewCount(1)
                .active(true)
                .verified(true)
                .available(true)
                .build());

        inactiveWorker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount3)
                .firstName("Jim")
                .lastName("Inactive")
                .email("worker3@analytics.test")
                .phoneNumber("+1999111444")
                .primaryCity("Austin")
                .category(inactiveCategory)
                .hourlyRate(BigDecimal.valueOf(40.00))
                .averageRating(BigDecimal.valueOf(3.0))
                .reviewCount(1)
                .active(false)
                .verified(false)
                .available(false)
                .build());
    }

    @Test
    void repeatedSameDayRankingRefreshesDoNotViolateUniqueConstraints() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Run refresh 1
        analyticsService.refreshSnapshot(today);

        // Run refresh 2 on same day - should be idempotent, no unique constraint violation
        assertDoesNotThrow(() -> analyticsService.refreshSnapshot(today));

        // Run refresh 3 on same day
        assertDoesNotThrow(() -> analyticsService.refreshSnapshot(today));

        assertTrue(snapshotRepository.findBySnapshotDate(today).isPresent());
        List<?> catRankings = categoryRankingRepository.findBySnapshotDateOrderByRankAsc(today);
        assertFalse(catRankings.isEmpty());
        List<?> workerRankings = workerRankingRepository.findBySnapshotDateOrderByRankAsc(today);
        assertFalse(workerRankings.isEmpty());
    }

    @Test
    void activeWorkerAnalyticsCountsActiveWorkersNotVerified() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        analyticsService.refreshSnapshot(today);

        var snapshot = snapshotRepository.findBySnapshotDate(today).orElseThrow();
        // activeWorker1 is active but unverified, activeWorker2 is active and verified, inactiveWorker is inactive
        // activeWorkers count must be 2, not 1!
        assertEquals(2, snapshot.getActiveWorkers());
    }

    @Test
    void ratingsCalculationUsesOnlyApprovedAndActiveReviews() {
        Booking booking1 = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker1)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now(ZoneOffset.UTC))
                .timeSlot("10:00-11:00")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .quotedAmount(BigDecimal.valueOf(50))
                .finalAmount(BigDecimal.valueOf(50))
                .description("Job 1")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(Instant.now().minusSeconds(3600))
                .build());

        Booking booking2 = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker2)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now(ZoneOffset.UTC))
                .timeSlot("12:00-13:00")
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .quotedAmount(BigDecimal.valueOf(60))
                .finalAmount(BigDecimal.valueOf(60))
                .description("Job 2")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(Instant.now().minusSeconds(1800))
                .build());

        Booking booking3 = bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(inactiveWorker)
                .category(inactiveCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now(ZoneOffset.UTC))
                .timeSlot("14:00-15:00")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .quotedAmount(BigDecimal.valueOf(40))
                .finalAmount(BigDecimal.valueOf(40))
                .description("Job 3")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(Instant.now().minusSeconds(900))
                .build());

        // Approved active review (rating 5)
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking1)
                .customer(customer)
                .worker(activeWorker1)
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        // Pending review (rating 1) - must NOT be included in platform rating!
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking2)
                .customer(customer)
                .worker(activeWorker2)
                .rating((short) 1)
                .active(true)
                .moderationStatus(ModerationStatus.PENDING)
                .build());

        // Inactive review (rating 1) - must NOT be included in platform rating!
        reviewRepository.saveAndFlush(Review.builder()
                .booking(booking3)
                .customer(customer)
                .worker(inactiveWorker)
                .rating((short) 1)
                .active(false)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

        AnalyticsOverviewResponse overview = analyticsService.getOverview();
        // Average should be 5.00, NOT diluted by the pending or inactive 1-star reviews
        assertEquals(new BigDecimal("5.00"), overview.averagePlatformRating());

        RatingDistributionResponse distribution = analyticsService.getRatingDistribution();
        assertEquals(1, distribution.totalReviews());
        assertEquals(1, distribution.distribution().get((short) 5));
        assertEquals(0, distribution.distribution().get((short) 1));
    }

    @Test
    void analyticsRespectDateRangesAndActiveStates() {
        LocalDate dateInMay = LocalDate.of(2026, 5, 10);
        LocalDate dateInJune = LocalDate.of(2026, 6, 15);

        // May booking
        bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker1)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(dateInMay)
                .timeSlot("10:00-11:00")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .quotedAmount(BigDecimal.valueOf(50))
                .finalAmount(BigDecimal.valueOf(50))
                .description("May job")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(dateInMay.atTime(11, 0).toInstant(ZoneOffset.UTC))
                .build());

        // June bookings: worker1 has 1 booking, worker2 has 2 bookings
        bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker1)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(dateInJune)
                .timeSlot("09:00-10:00")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .quotedAmount(BigDecimal.valueOf(50))
                .finalAmount(BigDecimal.valueOf(50))
                .description("June job 1")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(dateInJune.atTime(10, 0).toInstant(ZoneOffset.UTC))
                .build());

        bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker2)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(dateInJune)
                .timeSlot("10:00-11:00")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .quotedAmount(BigDecimal.valueOf(60))
                .finalAmount(BigDecimal.valueOf(60))
                .description("June job 2")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(dateInJune.atTime(11, 0).toInstant(ZoneOffset.UTC))
                .build());

        bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(activeWorker2)
                .category(activeCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(dateInJune)
                .timeSlot("14:00-15:00")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .quotedAmount(BigDecimal.valueOf(60))
                .finalAmount(BigDecimal.valueOf(60))
                .description("June job 3")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(dateInJune.atTime(15, 0).toInstant(ZoneOffset.UTC))
                .build());

        // Inactive worker booking in June - must NOT show in top workers
        bookingRepository.saveAndFlush(Booking.builder()
                .customer(customer)
                .worker(inactiveWorker)
                .category(inactiveCategory)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(dateInJune)
                .timeSlot("16:00-17:00")
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(17, 0))
                .quotedAmount(BigDecimal.valueOf(40))
                .finalAmount(BigDecimal.valueOf(40))
                .description("June inactive job")
                .addressLine1("123 Main St")
                .addressCity("Austin")
                .addressState("TX")
                .addressPincode("78701")
                .completedAt(dateInJune.atTime(17, 0).toInstant(ZoneOffset.UTC))
                .build());

        // Query June only:
        List<TopCategoryResponse> juneCategories = analyticsService.getTopCategories(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 10
        );
        assertEquals(1, juneCategories.size());
        assertEquals("Plumbing-Analytics", juneCategories.getFirst().categoryName());
        assertEquals(3, juneCategories.getFirst().bookingCount());

        List<TopWorkerResponse> juneWorkers = analyticsService.getTopWorkers(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 10
        );
        // Only activeWorker2 (2 bookings) and activeWorker1 (1 booking)
        assertEquals(2, juneWorkers.size());
        assertEquals(activeWorker2.getId(), juneWorkers.get(0).workerId());
        assertEquals(2, juneWorkers.get(0).completedBookings());
        assertEquals((short) 1, juneWorkers.get(0).rank());

        assertEquals(activeWorker1.getId(), juneWorkers.get(1).workerId());
        assertEquals(1, juneWorkers.get(1).completedBookings());
        assertEquals((short) 2, juneWorkers.get(1).rank());
    }

    @Test
    void searchPaginationReturnsCorrectTotalAndPageSize() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        short targetDayOfWeek = (short) targetDate.getDayOfWeek().getValue();

        // activeWorker1 has working hours on targetDate
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(activeWorker1)
                .dayOfWeek(targetDayOfWeek)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .active(true)
                .build());

        // activeWorker2 does NOT have working hours on targetDate (so unavailable)

        // Search with availableOn=targetDate and page size = 10
        WorkerSearchResponse response = workerSearchService.searchWorkers(
                null, null, null, null, false, null, null,
                targetDate, null, null, null, null, false,
                "createdAt", "asc", PageRequest.of(0, 10)
        );

        // Exactly 1 worker matches (activeWorker1). totalElements must be 1, results size must be 1
        assertEquals(1, response.totalElements());
        assertEquals(1, response.results().size());
        assertEquals(activeWorker1.getId(), response.results().getFirst().id());
    }

    @Test
    void changesToWorkerAvailabilityInvalidateAffectedCachedResults() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        short targetDayOfWeek = (short) targetDate.getDayOfWeek().getValue();

        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(activeWorker1)
                .dayOfWeek(targetDayOfWeek)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .active(true)
                .build());

        // Initial search - caches the result
        WorkerSearchResponse firstSearch = workerSearchService.searchWorkers(
                null, null, null, null, false, null, true,
                null, null, null, null, null, false,
                "createdAt", "asc", PageRequest.of(0, 10)
        );
        assertTrue(firstSearch.results().stream().anyMatch(w -> w.id().equals(activeWorker1.getId())));

        Cache cache = cacheManager.getCache(CacheConfig.WORKER_SEARCH_CACHE);
        assertNotNull(cache);

        // Mock security context as activeWorker1
        AuthenticatedUser authUser = new AuthenticatedUser(
                activeWorker1.getUserAccount().getId(),
                "worker1@analytics.test",
                UserRole.WORKER
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authUser, null, List.of(new SimpleGrantedAuthority("ROLE_WORKER")))
        );

        // Update activeWorker1 bookable status to false -> should evict cache
        availabilityService.updateBookable(new UpdateBookableRequest(false));

        // Subsequent search: activeWorker1 is no longer bookable (available=false)
        WorkerSearchResponse secondSearch = workerSearchService.searchWorkers(
                null, null, null, null, false, null, true,
                null, null, null, null, null, false,
                "createdAt", "asc", PageRequest.of(0, 10)
        );
        assertFalse(secondSearch.results().stream().anyMatch(w -> w.id().equals(activeWorker1.getId())));
    }

    @Test
    void searchCacheKeyGeneratorGeneratesCollisionResistantKeys() {
        Object key1 = searchCacheKeyGenerator.generate(null, null, "Austin", "TX", activeCategory.getId());
        Object key2 = searchCacheKeyGenerator.generate(null, null, "Austin", "TX", inactiveCategory.getId());
        Object key1Repeat = searchCacheKeyGenerator.generate(null, null, "Austin", "TX", activeCategory.getId());

        assertNotNull(key1);
        assertNotNull(key2);
        assertTrue(key1.toString().startsWith("search:"));
        // 64 hex characters + "search:" prefix = 71 chars
        assertEquals(71, key1.toString().length());
        assertEquals(key1, key1Repeat, "Identical query parameters must yield identical key");
        assertNotEquals(key1, key2, "Different query parameters must yield different keys (collision-resistant)");
    }
}
