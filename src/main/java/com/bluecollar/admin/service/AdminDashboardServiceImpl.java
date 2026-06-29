package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.DashboardResponse;
import com.bluecollar.admin.repository.AdminStatsRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final BookingRepository bookingRepository;
    private final AdminStatsRepository adminStatsRepository;

    @Override
    public DashboardResponse getDashboard(LocalDate fromDate, LocalDate toDate) {
        long totalCustomers = customerRepository.countByActiveTrue();
        long totalWorkers = workerRepository.countByActiveTrue();
        long totalBookings = bookingRepository.count();
        long activeBookings = bookingRepository.countByStatusIn(List.of(
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
                BookingStatus.IN_PROGRESS
        ));

        long completed = bookingRepository.countByStatus(BookingStatus.COMPLETED);
        long cancelled = bookingRepository.countByStatus(BookingStatus.CANCELLED);
        long rejected = bookingRepository.countByStatus(BookingStatus.REJECTED);
        long denominator = completed + cancelled + rejected;

        BigDecimal completionRate = denominator == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);

        return new DashboardResponse(
                totalCustomers,
                totalWorkers,
                totalBookings,
                activeBookings,
                completionRate,
                adminStatsRepository.getCategoryDistribution(),
                adminStatsRepository.getBookingsTrend(fromDate, toDate)
        );
    }
}
