package com.bluecollar.analytics.service;

import com.bluecollar.analytics.dto.*;
import com.bluecollar.analytics.entity.AnalyticsCategoryRanking;
import com.bluecollar.analytics.entity.AnalyticsDailySnapshot;
import com.bluecollar.analytics.entity.AnalyticsWorkerRanking;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.*;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final WorkerRepository workerRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;
    private final AnalyticsDailySnapshotRepository snapshotRepository;
    private final AnalyticsCategoryRankingRepository categoryRankingRepository;
    private final AnalyticsWorkerRankingRepository workerRankingRepository;

    @Override
    public AnalyticsOverviewResponse getOverview() {
        long totalWorkers = workerRepository.count();
        long activeWorkers = workerRepository.countByActiveTrue();
        long totalCustomers = customerRepository.count();
        long activeCustomers = customerRepository.countByActiveTrue();
        long totalBookings = bookingRepository.count();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long bookingsToday = bookingRepository.countByCreatedAtBetween(
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        Double avg = reviewRepository.findAverageRatingForActiveApprovedReviews();
        BigDecimal avgRating = avg == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenue = bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED);
        return new AnalyticsOverviewResponse(
                totalWorkers, activeWorkers, totalCustomers, activeCustomers,
                totalBookings, bookingsToday, avgRating, revenue == null ? BigDecimal.ZERO : revenue
        );
    }

    @Override
    public List<DailyBookingStatsResponse> getDailyBookings(LocalDate from, LocalDate to) {
        return snapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDateAsc(from, to).stream()
                .map(s -> new DailyBookingStatsResponse(
                        s.getSnapshotDate(), s.getBookingsCreated(),
                        s.getBookingsCompleted(), s.getBookingsCancelled()))
                .toList();
    }

    @Override
    public List<TopCategoryResponse> getTopCategories(LocalDate from, LocalDate to, int limit) {
        int pageLimit = Math.max(1, limit);
        if (from != null || to != null) {
            List<CategoryBookingStatsProjection> stats = bookingRepository.findTopCategoriesWithDateRange(
                    from, to, PageRequest.of(0, pageLimit)
            );
            List<TopCategoryResponse> responses = new ArrayList<>();
            short rank = 1;
            for (CategoryBookingStatsProjection stat : stats) {
                responses.add(new TopCategoryResponse(
                        stat.getCategoryId(),
                        stat.getCategoryName(),
                        (int) stat.getBookingCount(),
                        rank++
                ));
            }
            return responses;
        }

        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        List<AnalyticsCategoryRanking> rankings = categoryRankingRepository.findBySnapshotDateOrderByRankAsc(date);
        if (rankings.isEmpty()) {
            List<CategoryBookingStatsProjection> stats = bookingRepository.findTopCategoriesWithDateRange(
                    null, null, PageRequest.of(0, pageLimit)
            );
            List<TopCategoryResponse> responses = new ArrayList<>();
            short rank = 1;
            for (CategoryBookingStatsProjection stat : stats) {
                responses.add(new TopCategoryResponse(
                        stat.getCategoryId(),
                        stat.getCategoryName(),
                        (int) stat.getBookingCount(),
                        rank++
                ));
            }
            return responses;
        }
        return rankings.stream()
                .filter(r -> Boolean.TRUE.equals(r.getCategory().getActive()))
                .limit(pageLimit)
                .map(r -> new TopCategoryResponse(
                        r.getCategory().getId(),
                        r.getCategory().getName(),
                        r.getBookingCount(),
                        r.getRank()))
                .toList();
    }

    @Override
    public List<TopWorkerResponse> getTopWorkers(LocalDate from, LocalDate to, int limit) {
        int pageLimit = Math.max(1, limit);
        if (from != null || to != null) {
            List<WorkerBookingStatsProjection> stats = bookingRepository.findTopWorkersWithDateRange(
                    from, to, PageRequest.of(0, pageLimit)
            );
            List<TopWorkerResponse> responses = new ArrayList<>();
            short rank = 1;
            for (WorkerBookingStatsProjection stat : stats) {
                responses.add(new TopWorkerResponse(
                        stat.getWorkerId(),
                        stat.getFirstName() + " " + stat.getLastName(),
                        (int) stat.getCompletedBookings(),
                        rank++
                ));
            }
            return responses;
        }

        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        List<AnalyticsWorkerRanking> rankings = workerRankingRepository.findBySnapshotDateOrderByRankAsc(date);
        if (rankings.isEmpty()) {
            List<WorkerBookingStatsProjection> stats = bookingRepository.findTopWorkersWithDateRange(
                    null, null, PageRequest.of(0, pageLimit)
            );
            List<TopWorkerResponse> responses = new ArrayList<>();
            short rank = 1;
            for (WorkerBookingStatsProjection stat : stats) {
                responses.add(new TopWorkerResponse(
                        stat.getWorkerId(),
                        stat.getFirstName() + " " + stat.getLastName(),
                        (int) stat.getCompletedBookings(),
                        rank++
                ));
            }
            return responses;
        }
        return rankings.stream()
                .filter(r -> Boolean.TRUE.equals(r.getWorker().getActive()))
                .limit(pageLimit)
                .map(r -> new TopWorkerResponse(
                        r.getWorker().getId(),
                        r.getWorker().getFirstName() + " " + r.getWorker().getLastName(),
                        r.getCompletedBookings(),
                        r.getRank()))
                .toList();
    }

    @Override
    public RatingDistributionResponse getRatingDistribution() {
        Map<Short, Long> distribution = new HashMap<>();
        for (short i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        List<Object[]> rows = reviewRepository.countDistributionForActiveApprovedReviews();
        long totalReviews = 0;
        for (Object[] row : rows) {
            Short rating = ((Number) row[0]).shortValue();
            Long count = ((Number) row[1]).longValue();
            distribution.put(rating, count);
            totalReviews += count;
        }
        Double avg = reviewRepository.findAverageRatingForActiveApprovedReviews();
        BigDecimal average = avg == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        return new RatingDistributionResponse(distribution, average, totalReviews);
    }

    @Override
    @Transactional
    public void refreshSnapshot(LocalDate date) {
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        AnalyticsDailySnapshot snapshot = snapshotRepository.findBySnapshotDate(date)
                .orElse(AnalyticsDailySnapshot.builder().snapshotDate(date).build());
        snapshot.setTotalWorkers((int) workerRepository.count());
        snapshot.setActiveWorkers((int) workerRepository.countByActiveTrue());
        snapshot.setTotalCustomers((int) customerRepository.count());
        snapshot.setActiveCustomers((int) customerRepository.countByActiveTrue());
        snapshot.setBookingsCreated((int) bookingRepository.countByCreatedAtBetween(start, end));
        snapshot.setBookingsCompleted((int) bookingRepository.countByStatusAndCompletedAtBetween(
                BookingStatus.COMPLETED, start, end));
        snapshot.setBookingsCancelled((int) bookingRepository.countByStatusAndCancelledAtBetween(
                BookingStatus.CANCELLED, start, end));
        BigDecimal revenue = bookingRepository.sumFinalAmountByStatusAndCompletedAtBetween(
                BookingStatus.COMPLETED, start, end);
        snapshot.setTotalRevenue(revenue == null ? BigDecimal.ZERO : revenue);

        Double avg = reviewRepository.findAverageRatingForActiveApprovedReviewsCreatedBetween(start, end);
        snapshot.setAvgRating(avg == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        snapshotRepository.save(snapshot);

        refreshCategoryRankings(date);
        refreshWorkerRankings(date);
    }

    private void refreshCategoryRankings(LocalDate date) {
        List<CategoryRankingProjection> top = bookingRepository.findTopCategoriesForRanking(
                BookingStatus.COMPLETED, PageRequest.of(0, 10)
        );
        Set<UUID> rankedCategoryIds = new HashSet<>();
        List<AnalyticsCategoryRanking> rankings = new ArrayList<>();
        short rank = 1;
        for (CategoryRankingProjection item : top) {
            rankedCategoryIds.add(item.getCategory().getId());
            rankings.add(AnalyticsCategoryRanking.builder()
                    .snapshotDate(date)
                    .category(item.getCategory())
                    .bookingCount((int) item.getBookingCount())
                    .rank(rank++)
                    .build());
        }

        if (rankings.size() < 10) {
            int remaining = 10 - rankings.size();
            List<Category> otherCategories = categoryRepository.findByActiveTrue().stream()
                    .filter(c -> !rankedCategoryIds.contains(c.getId()))
                    .limit(remaining)
                    .toList();
            for (Category category : otherCategories) {
                rankings.add(AnalyticsCategoryRanking.builder()
                        .snapshotDate(date)
                        .category(category)
                        .bookingCount(0)
                        .rank(rank++)
                        .build());
            }
        }

        categoryRankingRepository.deleteBySnapshotDate(date);
        categoryRankingRepository.flush();
        categoryRankingRepository.saveAll(rankings);
    }

    private void refreshWorkerRankings(LocalDate date) {
        List<WorkerRankingProjection> top = bookingRepository.findTopWorkersForRanking(
                BookingStatus.COMPLETED, PageRequest.of(0, 10)
        );
        Set<UUID> rankedWorkerIds = new HashSet<>();
        List<AnalyticsWorkerRanking> rankings = new ArrayList<>();
        short rank = 1;
        for (WorkerRankingProjection item : top) {
            rankedWorkerIds.add(item.getWorker().getId());
            rankings.add(AnalyticsWorkerRanking.builder()
                    .snapshotDate(date)
                    .worker(item.getWorker())
                    .completedBookings((int) item.getBookingCount())
                    .rank(rank++)
                    .build());
        }

        if (rankings.size() < 10) {
            int remaining = 10 - rankings.size();
            List<Worker> otherWorkers = workerRepository.findByActiveTrue(PageRequest.of(0, remaining)).getContent().stream()
                    .filter(w -> !rankedWorkerIds.contains(w.getId()))
                    .limit(remaining)
                    .toList();
            for (Worker worker : otherWorkers) {
                rankings.add(AnalyticsWorkerRanking.builder()
                        .snapshotDate(date)
                        .worker(worker)
                        .completedBookings(0)
                        .rank(rank++)
                        .build());
            }
        }

        workerRankingRepository.deleteBySnapshotDate(date);
        workerRankingRepository.flush();
        workerRankingRepository.saveAll(rankings);
    }
}
