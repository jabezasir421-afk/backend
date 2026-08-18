package com.bluecollar.review.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.exception.BookingNotFoundException;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.common.event.ReviewCreatedEvent;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.service.CustomerServiceImpl;
import com.bluecollar.review.dto.*;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.ReportStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.entity.ReviewReport;
import com.bluecollar.review.exception.ReviewAlreadyExistsException;
import com.bluecollar.review.exception.ReviewNotAllowedException;
import com.bluecollar.review.exception.ReviewNotFoundException;
import com.bluecollar.review.exception.ReviewReportAlreadyExistsException;
import com.bluecollar.review.mapper.ReviewMapper;
import com.bluecollar.review.repository.ReviewReportRepository;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private static final Duration REVIEW_WINDOW = Duration.ofDays(30);
    private static final Set<String> BLOCKED_WORDS = Set.of("spam", "fake", "offensive");
    private static final int AUTO_MODERATE_REPORT_THRESHOLD = 3;

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final BookingRepository bookingRepository;
    private final CustomerServiceImpl customerService;
    private final WorkerRepository workerRepository;
    private final UserAccountRepository userAccountRepository;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReviewResponse createReview(CreateReviewRequest request) {
        Customer customer = customerService.findCustomerByCurrentUser();
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(request.bookingId()));

        validateReviewEligibility(booking, customer);

        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new ReviewAlreadyExistsException(booking.getId());
        }

        ModerationStatus moderationStatus = shouldAutoModerate(request.comment())
                ? ModerationStatus.PENDING
                : ModerationStatus.APPROVED;

        Review review = Review.builder()
                .booking(booking)
                .customer(customer)
                .worker(booking.getWorker())
                .rating(request.rating())
                .comment(request.comment() == null ? null : request.comment().trim())
                .moderationStatus(moderationStatus)
                .build();

        Review savedReview = reviewRepository.save(review);
        recalculateWorkerRating(booking.getWorker().getId());

        UUID workerUserId = booking.getWorker().getUserAccount() != null
                ? booking.getWorker().getUserAccount().getId()
                : null;
        if (workerUserId != null) {
            eventPublisher.publishEvent(new ReviewCreatedEvent(
                    savedReview.getId(),
                    workerUserId,
                    savedReview.getRating()
            ));
        }

        return reviewMapper.toResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getWorkerReviews(UUID workerId, Pageable pageable) {
        return reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(
                        workerId, ModerationStatus.APPROVED, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewStatsResponse getWorkerReviewStats(UUID workerId) {
        List<Review> reviews = reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(
                workerId, ModerationStatus.APPROVED);
        Map<Short, Long> distribution = new HashMap<>();
        for (short i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        for (Review review : reviews) {
            distribution.merge(review.getRating(), 1L, Long::sum);
        }
        BigDecimal average = reviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reviews.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        return new ReviewStatsResponse(average, reviews.size(), distribution);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(UUID id) {
        return reviewMapper.toResponse(findActiveReview(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> getAllReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable).map(this::toAdminResponse);
    }

    @Override
    public void deactivateReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException(id));
        review.setActive(false);
        reviewRepository.save(review);
        recalculateWorkerRating(review.getWorker().getId());
    }

    @Override
    public void activateReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException(id));
        review.setActive(true);
        reviewRepository.save(review);
        recalculateWorkerRating(review.getWorker().getId());
    }

    @Override
    public void reportReview(UUID reviewId, ReportReviewRequest request) {
        Review review = findActiveReview(reviewId);
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();

        if (review.getCustomer().getUserAccount().getId().equals(currentUser.userAccountId())) {
            throw new ReviewNotAllowedException("You cannot report your own review");
        }

        if (reviewReportRepository.existsByReviewIdAndReporterUserIdAndStatus(
                reviewId, currentUser.userAccountId(), ReportStatus.OPEN)) {
            throw new ReviewReportAlreadyExistsException(reviewId);
        }

        UserAccount reporter = userAccountRepository.findById(currentUser.userAccountId()).orElseThrow();
        ReviewReport report = ReviewReport.builder()
                .review(review)
                .reporterUser(reporter)
                .reason(request.reason())
                .description(request.description())
                .build();
        reviewReportRepository.save(report);

        long openReports = reviewReportRepository.countByReviewIdAndStatus(reviewId, ReportStatus.OPEN);
        if (openReports >= AUTO_MODERATE_REPORT_THRESHOLD
                && review.getModerationStatus() == ModerationStatus.APPROVED) {
            review.setModerationStatus(ModerationStatus.PENDING);
            reviewRepository.save(review);
            recalculateWorkerRating(review.getWorker().getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> getPendingReviews(Pageable pageable) {
        return reviewRepository.findByModerationStatus(ModerationStatus.PENDING, pageable)
                .map(this::toAdminResponse);
    }

    @Override
    public AdminReviewResponse approveReview(UUID id, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException(id));
        AuthenticatedUser admin = SecurityUtils.getCurrentUser();
        review.setModerationStatus(ModerationStatus.APPROVED);
        review.setModeratedBy(userAccountRepository.findById(admin.userAccountId()).orElseThrow());
        review.setModeratedAt(Instant.now());
        review.setModerationNotes(request.notes());
        reviewRepository.save(review);
        recalculateWorkerRating(review.getWorker().getId());
        return toAdminResponse(review);
    }

    @Override
    public AdminReviewResponse rejectReview(UUID id, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException(id));
        AuthenticatedUser admin = SecurityUtils.getCurrentUser();
        review.setModerationStatus(ModerationStatus.HIDDEN);
        review.setModeratedBy(userAccountRepository.findById(admin.userAccountId()).orElseThrow());
        review.setModeratedAt(Instant.now());
        review.setModerationNotes(request.notes());
        reviewRepository.save(review);
        recalculateWorkerRating(review.getWorker().getId());
        return toAdminResponse(review);
    }

    private AdminReviewResponse toAdminResponse(Review review) {
        long reportCount = reviewReportRepository.countByReviewIdAndStatus(review.getId(), ReportStatus.OPEN);
        return new AdminReviewResponse(
                review.getId(),
                review.getWorker().getId(),
                review.getCustomer().getId(),
                review.getRating(),
                review.getComment(),
                review.getModerationStatus(),
                review.getModeratedAt(),
                reportCount,
                Boolean.TRUE.equals(review.getActive())
        );
    }

    private boolean shouldAutoModerate(String comment) {
        if (comment == null || comment.isBlank()) {
            return false;
        }
        String lower = comment.toLowerCase(Locale.ROOT);
        return BLOCKED_WORDS.stream().anyMatch(lower::contains);
    }

    private Review findActiveReview(UUID id) {
        return reviewRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ReviewNotFoundException(id));
    }

    private void validateReviewEligibility(Booking booking, Customer customer) {
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ReviewNotAllowedException("You can only review your own bookings");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ReviewNotAllowedException("Reviews are allowed only for completed bookings");
        }
        if (booking.getCompletedAt() == null) {
            throw new ReviewNotAllowedException("Booking completion time is missing");
        }
        if (booking.getCompletedAt().plus(REVIEW_WINDOW).isBefore(Instant.now())) {
            throw new ReviewNotAllowedException("Review window of 30 days has expired");
        }
    }

    private void recalculateWorkerRating(UUID workerId) {
        Worker worker = workerRepository.findById(workerId).orElseThrow();
        List<Review> reviews = reviewRepository.findByWorkerIdAndActiveTrueAndModerationStatus(
                workerId, ModerationStatus.APPROVED);
        if (reviews.isEmpty()) {
            worker.setAverageRating(BigDecimal.ZERO);
            worker.setReviewCount(0);
        } else {
            double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
            worker.setAverageRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
            worker.setReviewCount(reviews.size());
        }
        workerRepository.save(worker);
    }
}
