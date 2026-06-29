package com.bluecollar.review.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.exception.BookingNotFoundException;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.common.event.ReviewCreatedEvent;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.dto.CustomerSummaryResponse;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.service.CustomerServiceImpl;
import com.bluecollar.review.dto.*;
import com.bluecollar.review.entity.*;
import com.bluecollar.review.exception.ReviewAlreadyExistsException;
import com.bluecollar.review.exception.ReviewNotAllowedException;
import com.bluecollar.review.exception.ReviewNotFoundException;
import com.bluecollar.review.exception.ReviewReportAlreadyExistsException;
import com.bluecollar.review.mapper.ReviewMapper;
import com.bluecollar.review.repository.ReviewReportRepository;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewReportRepository reviewReportRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CustomerServiceImpl customerService;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID customerId;
    private UUID workerId;
    private UUID bookingId;
    private UUID reviewId;
    private UUID customerUserId;
    private UUID workerUserId;
    private UUID adminUserId;

    private Customer customer;
    private Worker worker;
    private Booking booking;
    private Review review;
    private ReviewResponse reviewResponse;
    private UserAccount customerUserAccount;
    private UserAccount workerUserAccount;
    private UserAccount adminUserAccount;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        reviewId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();

        customerUserAccount = new UserAccount();
        customerUserAccount.setId(customerUserId);

        workerUserAccount = new UserAccount();
        workerUserAccount.setId(workerUserId);

        adminUserAccount = new UserAccount();
        adminUserAccount.setId(adminUserId);

        customer = new Customer();
        customer.setId(customerId);
        customer.setUserAccount(customerUserAccount);

        worker = Worker.builder()
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build();
        worker.setId(workerId);
        worker.setUserAccount(workerUserAccount);

        booking = Booking.builder()
                .customer(customer)
                .worker(worker)
                .status(BookingStatus.COMPLETED)
                .completedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
        booking.setId(bookingId);

        review = Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 5)
                .comment("Great service!")
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();
        review.setId(reviewId);

        reviewResponse = new ReviewResponse(
                reviewId,
                bookingId,
                workerId,
                new CustomerSummaryResponse(customerId, "John", "Doe"),
                (short) 5,
                "Great service!",
                true,
                Instant.now(),
                Instant.now()
        );

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void createReviewShouldCreateApprovedReviewWhenEligibilityPassesAndNoSpam() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(review);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of(review));
        when(reviewMapper.toResponse(review)).thenReturn(reviewResponse);

        ReviewResponse response = reviewService.createReview(request);

        assertNotNull(response);
        assertEquals(reviewResponse, response);
        verify(eventPublisher, times(1)).publishEvent(any(ReviewCreatedEvent.class));
        verify(workerRepository, times(1)).save(worker);
    }

    @Test
    void createReviewShouldCreatePendingReviewWhenCommentContainsBlockedWords() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 2, "This is fake news!");

        Review pendingReview = Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(worker)
                .rating((short) 2)
                .comment("This is fake news!")
                .active(true)
                .moderationStatus(ModerationStatus.PENDING)
                .build();
        pendingReview.setId(reviewId);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(pendingReview);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of());
        when(reviewMapper.toResponse(pendingReview)).thenReturn(reviewResponse);

        reviewService.createReview(request);

        verify(eventPublisher, times(1)).publishEvent(any(ReviewCreatedEvent.class));
    }

    @Test
    void createReviewShouldThrowWhenBookingNotFound() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> reviewService.createReview(request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewShouldThrowWhenBookingDoesNotBelongToCustomer() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");
        Customer otherCustomer = new Customer();
        otherCustomer.setId(UUID.randomUUID());
        booking.setCustomer(otherCustomer);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ReviewNotAllowedException.class, () -> reviewService.createReview(request));
    }

    @Test
    void createReviewShouldThrowWhenBookingNotCompleted() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");
        booking.setStatus(BookingStatus.ACCEPTED);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ReviewNotAllowedException.class, () -> reviewService.createReview(request));
    }

    @Test
    void createReviewShouldThrowWhenBookingCompletionTimeMissing() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");
        booking.setCompletedAt(null);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ReviewNotAllowedException.class, () -> reviewService.createReview(request));
    }

    @Test
    void createReviewShouldThrowWhenReviewWindowExpired() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");
        booking.setCompletedAt(Instant.now().minus(35, ChronoUnit.DAYS));

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ReviewNotAllowedException.class, () -> reviewService.createReview(request));
    }

    @Test
    void createReviewShouldThrowWhenReviewAlreadyExistsForBooking() {
        CreateReviewRequest request = new CreateReviewRequest(bookingId, (short) 5, "Great service!");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(true);

        assertThrows(ReviewAlreadyExistsException.class, () -> reviewService.createReview(request));
    }

    @Test
    void getWorkerReviewsShouldReturnPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> reviewPage = new PageImpl<>(List.of(review), pageable, 1);

        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED, pageable))
                .thenReturn(reviewPage);
        when(reviewMapper.toResponse(review)).thenReturn(reviewResponse);

        Page<ReviewResponse> result = reviewService.getWorkerReviews(workerId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(reviewRepository).findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED, pageable);
    }

    @Test
    void getWorkerReviewStatsShouldReturnStats() {
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of(review));

        ReviewStatsResponse stats = reviewService.getWorkerReviewStats(workerId);

        assertNotNull(stats);
        assertEquals(BigDecimal.valueOf(5.00).setScale(2), stats.averageRating());
        assertEquals(1L, stats.totalReviews());
        assertEquals(1L, stats.ratingDistribution().get((short) 5));
    }

    @Test
    void getReviewByIdShouldReturnResponseWhenReviewActive() {
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.of(review));
        when(reviewMapper.toResponse(review)).thenReturn(reviewResponse);

        ReviewResponse response = reviewService.getReviewById(reviewId);

        assertNotNull(response);
        assertEquals(reviewResponse, response);
    }

    @Test
    void getReviewByIdShouldThrowWhenNotFound() {
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.empty());

        assertThrows(ReviewNotFoundException.class, () -> reviewService.getReviewById(reviewId));
    }

    @Test
    void deactivateReviewShouldSetInactiveAndRecalculate() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of());

        reviewService.deactivateReview(reviewId);

        assertEquals(false, review.getActive());
        verify(reviewRepository).save(review);
        verify(workerRepository).save(worker);
    }

    @Test
    void reportReviewShouldSaveReport() {
        ReportReviewRequest request = new ReportReviewRequest(ReportReason.SPAM, "Spammy comment");
        UUID otherUserId = UUID.randomUUID();
        AuthenticatedUser reporterUser = new AuthenticatedUser(otherUserId, "reporter", UserRole.CUSTOMER);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(reporterUser);
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReviewIdAndReporterUserIdAndStatus(reviewId, otherUserId, ReportStatus.OPEN))
                .thenReturn(false);
        when(userAccountRepository.findById(otherUserId)).thenReturn(Optional.of(customerUserAccount));
        when(reviewReportRepository.countByReviewIdAndStatus(reviewId, ReportStatus.OPEN)).thenReturn(1L);

        reviewService.reportReview(reviewId, request);

        verify(reviewReportRepository).save(any(ReviewReport.class));
    }

    @Test
    void reportReviewShouldThrowWhenUserReportsOwnReview() {
        ReportReviewRequest request = new ReportReviewRequest(ReportReason.SPAM, "Spammy comment");
        AuthenticatedUser authorUser = new AuthenticatedUser(customerUserId, "author", UserRole.CUSTOMER);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(authorUser);
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.of(review));

        assertThrows(ReviewNotAllowedException.class, () -> reviewService.reportReview(reviewId, request));
    }

    @Test
    void reportReviewShouldThrowWhenReportAlreadyExists() {
        ReportReviewRequest request = new ReportReviewRequest(ReportReason.SPAM, "Spammy comment");
        UUID otherUserId = UUID.randomUUID();
        AuthenticatedUser reporterUser = new AuthenticatedUser(otherUserId, "reporter", UserRole.CUSTOMER);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(reporterUser);
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReviewIdAndReporterUserIdAndStatus(reviewId, otherUserId, ReportStatus.OPEN))
                .thenReturn(true);

        assertThrows(ReviewReportAlreadyExistsException.class, () -> reviewService.reportReview(reviewId, request));
    }

    @Test
    void reportReviewShouldTriggerAutoModerateWhenThresholdReached() {
        ReportReviewRequest request = new ReportReviewRequest(ReportReason.SPAM, "Spammy comment");
        UUID otherUserId = UUID.randomUUID();
        AuthenticatedUser reporterUser = new AuthenticatedUser(otherUserId, "reporter", UserRole.CUSTOMER);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(reporterUser);
        when(reviewRepository.findByIdAndActiveTrue(reviewId)).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReviewIdAndReporterUserIdAndStatus(reviewId, otherUserId, ReportStatus.OPEN))
                .thenReturn(false);
        when(userAccountRepository.findById(otherUserId)).thenReturn(Optional.of(customerUserAccount));
        when(reviewReportRepository.countByReviewIdAndStatus(reviewId, ReportStatus.OPEN)).thenReturn(3L);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of());

        reviewService.reportReview(reviewId, request);

        assertEquals(ModerationStatus.PENDING, review.getModerationStatus());
        verify(reviewRepository, times(1)).save(review);
    }

    @Test
    void approveReviewShouldSetStatusApprovedAndRecalculate() {
        ModerateReviewRequest request = new ModerateReviewRequest("Approved notes");
        AuthenticatedUser adminUser = new AuthenticatedUser(adminUserId, "admin", UserRole.ADMIN);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(adminUser);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(userAccountRepository.findById(adminUserId)).thenReturn(Optional.of(adminUserAccount));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of(review));

        AdminReviewResponse response = reviewService.approveReview(reviewId, request);

        assertNotNull(response);
        assertEquals(ModerationStatus.APPROVED, review.getModerationStatus());
        assertEquals("Approved notes", review.getModerationNotes());
        verify(reviewRepository).save(review);
    }

    @Test
    void rejectReviewShouldSetStatusHiddenAndRecalculate() {
        ModerateReviewRequest request = new ModerateReviewRequest("Inappropriate comment");
        AuthenticatedUser adminUser = new AuthenticatedUser(adminUserId, "admin", UserRole.ADMIN);

        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(adminUser);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(userAccountRepository.findById(adminUserId)).thenReturn(Optional.of(adminUserAccount));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(workerId, ModerationStatus.APPROVED))
                .thenReturn(List.of());

        AdminReviewResponse response = reviewService.rejectReview(reviewId, request);

        assertNotNull(response);
        assertEquals(ModerationStatus.HIDDEN, review.getModerationStatus());
        verify(reviewRepository).save(review);
    }
}
