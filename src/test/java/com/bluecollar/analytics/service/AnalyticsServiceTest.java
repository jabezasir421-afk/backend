package com.bluecollar.analytics.service;

import com.bluecollar.analytics.dto.AnalyticsOverviewResponse;
import com.bluecollar.analytics.dto.RatingDistributionResponse;
import com.bluecollar.analytics.entity.AnalyticsDailySnapshot;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AnalyticsDailySnapshotRepository snapshotRepository;

    @Mock
    private AnalyticsCategoryRankingRepository categoryRankingRepository;

    @Mock
    private AnalyticsWorkerRankingRepository workerRankingRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private Review approvedReview;

    @BeforeEach
    void setUp() {
        approvedReview = Review.builder()
                .rating((short) 5)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();
        approvedReview.setCreatedAt(Instant.now());
    }

    @Test
    void getOverviewShouldAggregatePlatformMetrics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(workerRepository.count()).thenReturn(10L);
        when(workerRepository.countByActiveTrue()).thenReturn(8L);
        when(customerRepository.count()).thenReturn(20L);
        when(customerRepository.countByActiveTrue()).thenReturn(18L);
        when(bookingRepository.count()).thenReturn(50L);
        when(bookingRepository.countByCreatedAtBetween(
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        )).thenReturn(3L);
        when(reviewRepository.findAll()).thenReturn(List.of(approvedReview));
        when(bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED)).thenReturn(new BigDecimal("1500.50"));

        AnalyticsOverviewResponse result = analyticsService.getOverview();

        assertEquals(10L, result.totalWorkers());
        assertEquals(8L, result.activeWorkers());
        assertEquals(20L, result.totalCustomers());
        assertEquals(18L, result.activeCustomers());
        assertEquals(50L, result.totalBookings());
        assertEquals(3L, result.bookingsToday());
        assertEquals(new BigDecimal("5.00"), result.averagePlatformRating());
        assertEquals(new BigDecimal("1500.50"), result.totalRevenue());
    }

    @Test
    void getRatingDistributionShouldReturnCountsAndAverage() {
        Review fourStarReview = Review.builder()
                .rating((short) 4)
                .active(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();

        when(reviewRepository.findAll()).thenReturn(List.of(approvedReview, fourStarReview));

        RatingDistributionResponse result = analyticsService.getRatingDistribution();

        assertEquals(2L, result.totalReviews());
        assertEquals(new BigDecimal("4.50"), result.average());
        assertEquals(1L, result.distribution().get((short) 5));
        assertEquals(1L, result.distribution().get((short) 4));
        assertEquals(0L, result.distribution().get((short) 1));
    }

    @Test
    void refreshSnapshotShouldPersistDailySnapshotAndRankings() {
        LocalDate date = LocalDate.of(2026, 6, 27);
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        when(snapshotRepository.findBySnapshotDate(date)).thenReturn(Optional.empty());
        when(workerRepository.count()).thenReturn(5L);
        when(workerRepository.countByVerifiedTrue()).thenReturn(3L);
        when(customerRepository.count()).thenReturn(12L);
        when(customerRepository.countByActiveTrue()).thenReturn(10L);
        when(bookingRepository.countByCreatedAtBetween(start, end)).thenReturn(4L);
        when(bookingRepository.countByStatusAndCompletedAtBetween(BookingStatus.COMPLETED, start, end)).thenReturn(2L);
        when(bookingRepository.countByStatusAndCancelledAtBetween(BookingStatus.CANCELLED, start, end)).thenReturn(1L);
        when(bookingRepository.sumFinalAmountByStatusAndCompletedAtBetween(BookingStatus.COMPLETED, start, end))
                .thenReturn(new BigDecimal("250.00"));
        when(reviewRepository.findAll()).thenReturn(List.of(approvedReview));
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(workerRepository.findAll()).thenReturn(List.of());

        analyticsService.refreshSnapshot(date);

        verify(snapshotRepository).save(any(AnalyticsDailySnapshot.class));
        verify(categoryRankingRepository).saveAll(eq(List.of()));
        verify(workerRankingRepository).saveAll(eq(List.of()));
    }
}
