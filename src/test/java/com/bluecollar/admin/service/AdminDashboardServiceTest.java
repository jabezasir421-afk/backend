package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.BookingsTrendResponse;
import com.bluecollar.admin.dto.CategoryDistributionResponse;
import com.bluecollar.admin.dto.DashboardResponse;
import com.bluecollar.admin.repository.AdminStatsRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AdminStatsRepository adminStatsRepository;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    @Test
    void getDashboardShouldAggregateDashboardMetrics() {
        LocalDate fromDate = LocalDate.of(2026, 6, 1);
        LocalDate toDate = LocalDate.of(2026, 6, 30);
        List<CategoryDistributionResponse> categoryDistribution = List.of(
                new CategoryDistributionResponse("Plumbing", 5L)
        );
        List<BookingsTrendResponse> bookingsTrend = List.of(
                new BookingsTrendResponse(LocalDate.of(2026, 6, 15), 3L)
        );

        when(customerRepository.countByActiveTrue()).thenReturn(20L);
        when(workerRepository.countByActiveTrue()).thenReturn(10L);
        when(bookingRepository.count()).thenReturn(100L);
        when(bookingRepository.countByStatusIn(List.of(
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
                BookingStatus.IN_PROGRESS
        ))).thenReturn(15L);
        when(bookingRepository.countByStatus(BookingStatus.COMPLETED)).thenReturn(60L);
        when(bookingRepository.countByStatus(BookingStatus.CANCELLED)).thenReturn(20L);
        when(bookingRepository.countByStatus(BookingStatus.REJECTED)).thenReturn(5L);
        when(adminStatsRepository.getCategoryDistribution()).thenReturn(categoryDistribution);
        when(adminStatsRepository.getBookingsTrend(fromDate, toDate)).thenReturn(bookingsTrend);

        DashboardResponse result = adminDashboardService.getDashboard(fromDate, toDate);

        assertEquals(20L, result.totalCustomers());
        assertEquals(10L, result.totalWorkers());
        assertEquals(100L, result.totalBookings());
        assertEquals(15L, result.activeBookings());
        assertEquals(new BigDecimal("70.59"), result.completionRate());
        assertEquals(categoryDistribution, result.categoriesDistribution());
        assertEquals(bookingsTrend, result.bookingsTrend());
    }
}
