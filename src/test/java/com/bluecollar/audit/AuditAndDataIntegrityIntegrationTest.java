package com.bluecollar.audit;

import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.address.service.AddressService;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.admin.service.AdminUserService;
import com.bluecollar.admin.service.AdminWorkerService;
import com.bluecollar.audit.entity.AuditLog;
import com.bluecollar.audit.repository.AuditLogRepository;
import com.bluecollar.auth.dto.RegisterWorkerRequest;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.service.AuthService;
import com.bluecollar.booking.dto.CancelBookingRequest;
import com.bluecollar.booking.dto.CreateBookingRequest;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.booking.service.BookingService;
import com.bluecollar.category.dto.CreateCategoryRequest;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.category.service.CategoryService;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.review.dto.ModerateReviewRequest;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.review.service.ReviewService;
import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.skill.service.SkillService;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import com.bluecollar.worker.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class AuditAndDataIntegrityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private WorkerIdentityDocumentRepository documentRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private AdminWorkerService adminWorkerService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SkillService skillService;

    @Autowired
    private WorkerService workerService;

    @Autowired
    private AddressService addressService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void sensitiveAdminOperationsProduceAuditRecords() {
        // Create Admin user
        UserAccount adminAccount = userAccountRepository.save(UserAccount.builder()
                .email("admin.audit@example.com")
                .phoneNumber("+919999900001")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .active(true)
                .build());

        // Create target user
        UserAccount targetUser = userAccountRepository.save(UserAccount.builder()
                .email("target.audit@example.com")
                .phoneNumber("+919999900002")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        try (MockedStatic<SecurityUtils> securityMock = Mockito.mockStatic(SecurityUtils.class)) {
            securityMock.when(SecurityUtils::isAuthenticated).thenReturn(true);
            securityMock.when(SecurityUtils::getCurrentUser).thenReturn(
                    new AuthenticatedUser(adminAccount.getId(), adminAccount.getEmail(), UserRole.ADMIN));

            // 1. Deactivate user
            adminUserService.deactivateUser(targetUser.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "USER_DEACTIVATED".equals(log.getAction())
                            && targetUser.getId().equals(log.getEntityId())
                            && adminAccount.getId().equals(log.getActorUserId())));

            // 2. Activate user
            adminUserService.activateUser(targetUser.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "USER_ACTIVATED".equals(log.getAction())
                            && targetUser.getId().equals(log.getEntityId())));

            // 3. Change role
            adminUserService.changeRole(targetUser.getId(), UserRole.WORKER);
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "USER_ROLE_CHANGED".equals(log.getAction())
                            && targetUser.getId().equals(log.getEntityId())));
        }
    }

    @Test
    void workerVerificationAndDocumentReviewProduceAuditRecords() {
        UserAccount adminAccount = userAccountRepository.save(UserAccount.builder()
                .email("admin.worker.audit@example.com")
                .phoneNumber("+919999900011")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .active(true)
                .build());

        UserAccount workerUser = userAccountRepository.save(UserAccount.builder()
                .email("worker.audit@example.com")
                .phoneNumber("+919999900012")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Audit Plumbing")
                .active(true)
                .build());

        Worker worker = workerRepository.save(Worker.builder()
                .userAccount(workerUser)
                .firstName("Audit")
                .lastName("Worker")
                .phoneNumber("+919999900012")
                .email("worker.audit@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .active(true)
                .verified(false)
                .build());

        StoredFile storedFile = storedFileRepository.save(StoredFile.builder()
                .storageKey("id.pdf")
                .originalName("id.pdf")
                .mimeType("application/pdf")
                .sizeBytes(1024L)
                .entityType(com.bluecollar.storage.entity.EntityType.WORKER)
                .fileCategory(FileCategory.IDENTITY_DOC)
                .ownerUserId(workerUser.getId())
                .active(true)
                .build());

        WorkerIdentityDocument doc = documentRepository.save(WorkerIdentityDocument.builder()
                .worker(worker)
                .fileId(storedFile.getId())
                .documentType(DocumentType.AADHAAR)
                .documentNumber("enc-123")
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build());

        try (MockedStatic<SecurityUtils> securityMock = Mockito.mockStatic(SecurityUtils.class)) {
            securityMock.when(SecurityUtils::isAuthenticated).thenReturn(true);
            securityMock.when(SecurityUtils::getCurrentUser).thenReturn(
                    new AuthenticatedUser(adminAccount.getId(), adminAccount.getEmail(), UserRole.ADMIN));

            // Approve document
            adminWorkerService.approveIdentityDocument(doc.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "IDENTITY_DOCUMENT_APPROVED".equals(log.getAction())
                            && doc.getId().equals(log.getEntityId())));

            // Verify worker
            adminWorkerService.verifyWorker(worker.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "WORKER_VERIFIED".equals(log.getAction())
                            && worker.getId().equals(log.getEntityId())));

            // Unverify worker
            adminWorkerService.unverifyWorker(worker.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "WORKER_UNVERIFIED".equals(log.getAction())
                            && worker.getId().equals(log.getEntityId())));

            // Deactivate worker
            adminWorkerService.deactivateWorker(worker.getId());
            assertTrue(auditLogRepository.findAll().stream()
                    .anyMatch(log -> "WORKER_DEACTIVATED".equals(log.getAction())
                            && worker.getId().equals(log.getEntityId())));
        }
    }

    @Test
    void pendingAndHiddenReviewsAreNotExposedPublicly() throws Exception {
        UserAccount customerUser = userAccountRepository.save(UserAccount.builder()
                .email("rev.cust@example.com")
                .phoneNumber("+919999900021")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .userAccount(customerUser)
                .firstName("Rev")
                .lastName("Customer")
                .active(true)
                .build());

        UserAccount workerUser = userAccountRepository.save(UserAccount.builder()
                .email("rev.worker@example.com")
                .phoneNumber("+919999900022")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Rev Category")
                .active(true)
                .build());

        Worker worker = workerRepository.save(Worker.builder()
                .userAccount(workerUser)
                .firstName("Rev")
                .lastName("Worker")
                .phoneNumber("+919999900022")
                .email("rev.worker@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .active(true)
                .build());

        Address address = addressRepository.save(Address.builder()
                .customer(customer)
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("Line 1")
                .city("City")
                .state("State")
                .pincode("400001")
                .active(true)
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now().minusDays(5))
                .timeSlot("10:00-12:00")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .description("Fix pipe")
                .quotedAmount(BigDecimal.valueOf(100))
                .addressLine1("Line 1")
                .addressCity("City")
                .addressState("State")
                .addressPincode("400001")
                .build());

        Booking booking2 = bookingRepository.save(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now().minusDays(4))
                .timeSlot("14:00-16:00")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .description("Fix second pipe")
                .quotedAmount(BigDecimal.valueOf(100))
                .addressLine1("Line 1")
                .addressCity("City")
                .addressState("State")
                .addressPincode("400001")
                .build());

        // Create a PENDING review
        Review pendingReview = reviewRepository.save(Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .comment("Spam review containing spam")
                .moderationStatus(ModerationStatus.PENDING)
                .active(true)
                .build());

        // Create a HIDDEN review
        Review hiddenReview = reviewRepository.save(Review.builder()
                .booking(booking2)
                .customer(customer)
                .worker(worker)
                .rating((short) 1)
                .comment("Hidden review")
                .moderationStatus(ModerationStatus.HIDDEN)
                .active(true)
                .build());

        // Public GET /api/v1/reviews/{id} on PENDING review should return 404
        mockMvc.perform(get("/api/v1/reviews/{id}", pendingReview.getId()))
                .andExpect(status().isNotFound());

        // Public GET /api/v1/reviews/{id} on HIDDEN review should return 404
        mockMvc.perform(get("/api/v1/reviews/{id}", hiddenReview.getId()))
                .andExpect(status().isNotFound());

        // Public GET /api/v1/reviews/worker/{workerId} should NOT list pending or hidden reviews
        mockMvc.perform(get("/api/v1/reviews/worker/{workerId}", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void softDeletedRecordsRetainHistoricalBookingRelationships() {
        Category category = categoryRepository.save(Category.builder()
                .name("Historical Category")
                .active(true)
                .build());

        UserAccount customerUser = userAccountRepository.save(UserAccount.builder()
                .email("hist.cust@example.com")
                .phoneNumber("+919999900031")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .userAccount(customerUser)
                .firstName("Hist")
                .lastName("Customer")
                .active(true)
                .build());

        UserAccount workerUser = userAccountRepository.save(UserAccount.builder()
                .email("hist.worker@example.com")
                .phoneNumber("+919999900032")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .build());

        Worker worker = workerRepository.save(Worker.builder()
                .userAccount(workerUser)
                .firstName("Hist")
                .lastName("Worker")
                .phoneNumber("+919999900032")
                .email("hist.worker@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .active(true)
                .build());

        Address address = addressRepository.save(Address.builder()
                .customer(customer)
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("Line 1")
                .city("City")
                .state("State")
                .pincode("400001")
                .active(true)
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.COMPLETED)
                .scheduledDate(LocalDate.now().minusDays(2))
                .timeSlot("10:00-12:00")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .description("Historical booking")
                .quotedAmount(BigDecimal.valueOf(100))
                .addressLine1("Line 1")
                .addressCity("City")
                .addressState("State")
                .addressPincode("400001")
                .build());

        // Soft delete category and worker
        categoryService.deleteCategory(category.getId());
        workerService.deleteWorker(worker.getId());

        // Category and worker still exist in DB with active = false
        Category updatedCategory = categoryRepository.findById(category.getId()).orElseThrow();
        assertFalse(updatedCategory.getActive());

        Worker updatedWorker = workerRepository.findById(worker.getId()).orElseThrow();
        assertFalse(updatedWorker.getActive());

        // Historical booking still links to the category and worker without constraint failure
        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertNotNull(reloadedBooking.getCategory());
        assertEquals(category.getId(), reloadedBooking.getCategory().getId());
        assertEquals("Historical Category", reloadedBooking.getCategory().getName());

        assertNotNull(reloadedBooking.getWorker());
        assertEquals(worker.getId(), reloadedBooking.getWorker().getId());
        assertEquals("Hist", reloadedBooking.getWorker().getFirstName());
    }

    @Test
    void inactiveCategoriesAndSkillsCannotBeAssigned() {
        Category inactiveCategory = categoryRepository.save(Category.builder()
                .name("Inactive Category Test")
                .active(false)
                .build());

        Skill inactiveSkill = skillRepository.save(Skill.builder()
                .name("Inactive Skill Test")
                .active(false)
                .build());

        Skill activeSkill = skillRepository.save(Skill.builder()
                .name("Active Skill Test")
                .active(true)
                .build());

        RegisterWorkerRequest requestWithInactiveCategory = new RegisterWorkerRequest(
                "worker.inactive.cat@example.com",
                "+919999900041",
                "Password123!",
                "Inactive",
                "Worker",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Bio",
                BigDecimal.valueOf(50),
                inactiveCategory.getId(),
                Set.of(activeSkill.getId())
        );

        // Attempting to register worker with inactive category must fail
        IllegalArgumentException catEx = assertThrows(IllegalArgumentException.class, () ->
                authService.registerWorker(requestWithInactiveCategory));
        assertTrue(catEx.getMessage().contains("Cannot assign inactive category"));

        Category activeCategory = categoryRepository.save(Category.builder()
                .name("Active Cat For Skill Test")
                .active(true)
                .build());

        RegisterWorkerRequest requestWithInactiveSkill = new RegisterWorkerRequest(
                "worker.inactive.skill@example.com",
                "+919999900042",
                "Password123!",
                "Inactive",
                "Worker",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Bio",
                BigDecimal.valueOf(50),
                activeCategory.getId(),
                Set.of(inactiveSkill.getId())
        );

        // Attempting to register worker with inactive skill must fail
        IllegalArgumentException skillEx = assertThrows(IllegalArgumentException.class, () ->
                authService.registerWorker(requestWithInactiveSkill));
        assertTrue(skillEx.getMessage().contains("Cannot assign inactive skill"));
    }

    @Test
    void inactiveAddressesCannotBeUsedForBooking() {
        UserAccount customerUser = userAccountRepository.save(UserAccount.builder()
                .email("addr.cust@example.com")
                .phoneNumber("+919999900051")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .userAccount(customerUser)
                .firstName("Addr")
                .lastName("Customer")
                .active(true)
                .build());

        Address inactiveAddress = addressRepository.save(Address.builder()
                .customer(customer)
                .label("Old Home")
                .addressType(AddressType.HOME)
                .line1("Old Line")
                .city("City")
                .state("State")
                .pincode("400001")
                .active(false)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Addr Category")
                .active(true)
                .build());

        UserAccount workerUser = userAccountRepository.save(UserAccount.builder()
                .email("addr.worker@example.com")
                .phoneNumber("+919999900052")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .build());

        Worker worker = workerRepository.save(Worker.builder()
                .userAccount(workerUser)
                .firstName("Addr")
                .lastName("Worker")
                .phoneNumber("+919999900052")
                .email("addr.worker@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .active(true)
                .build());

        CreateBookingRequest request = new CreateBookingRequest(
                worker.getId(),
                category.getId(),
                inactiveAddress.getId(),
                LocalDate.now().plusDays(2),
                "10:00-12:00",
                "Fix tap"
        );

        try (MockedStatic<SecurityUtils> securityMock = Mockito.mockStatic(SecurityUtils.class)) {
            securityMock.when(SecurityUtils::isAuthenticated).thenReturn(true);
            securityMock.when(SecurityUtils::getCurrentUser).thenReturn(
                    new AuthenticatedUser(customerUser.getId(), customerUser.getEmail(), UserRole.CUSTOMER));

            // Creating booking with inactive address must be rejected
            assertThrows(Exception.class, () -> bookingService.createBooking(request));
        }
    }

    @Test
    void concurrentAddressCreationRespectsMaxLimit() throws InterruptedException {
        UserAccount customerUser = userAccountRepository.save(UserAccount.builder()
                .email("concurrent.addr@example.com")
                .phoneNumber("+919999900061")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .userAccount(customerUser)
                .firstName("Concurrent")
                .lastName("Customer")
                .active(true)
                .build());

        // Prepopulate with 8 addresses (limit is 10)
        for (int i = 0; i < 8; i++) {
            addressRepository.save(Address.builder()
                    .customer(customer)
                    .label("Address " + i)
                    .addressType(AddressType.OTHER)
                    .line1("Line " + i)
                    .city("City")
                    .state("State")
                    .pincode("400001")
                    .active(true)
                    .build());
        }

        // Now attempt 5 concurrent address creations (which would total 13, exceeding 10)
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    try (MockedStatic<SecurityUtils> securityMock = Mockito.mockStatic(SecurityUtils.class)) {
                        securityMock.when(SecurityUtils::isAuthenticated).thenReturn(true);
                        securityMock.when(SecurityUtils::getCurrentUser).thenReturn(
                                new AuthenticatedUser(customerUser.getId(), customerUser.getEmail(), UserRole.CUSTOMER));

                        CreateAddressRequest req = new CreateAddressRequest(
                                "Concurrent " + index,
                                AddressType.OTHER,
                                "Line " + index,
                                null,
                                null,
                                "City",
                                "State",
                                "400001",
                                null,
                                null,
                                false
                        );
                        addressService.createAddress(req);
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly 2 creations can succeed (8 + 2 = 10), remaining 3 must fail
        assertEquals(2, successCount.get());
        assertEquals(3, failureCount.get());
        assertEquals(10, addressRepository.countByCustomerIdAndActiveTrue(customer.getId()));
    }
}
