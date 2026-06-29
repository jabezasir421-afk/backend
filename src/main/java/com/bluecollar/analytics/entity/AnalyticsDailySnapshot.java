package com.bluecollar.analytics.entity;

import com.bluecollar.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "analytics_daily_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsDailySnapshot extends BaseEntity {

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "total_workers", nullable = false)
    private Integer totalWorkers;

    @Column(name = "active_workers", nullable = false)
    private Integer activeWorkers;

    @Column(name = "total_customers", nullable = false)
    private Integer totalCustomers;

    @Column(name = "active_customers", nullable = false)
    private Integer activeCustomers;

    @Column(name = "bookings_created", nullable = false)
    private Integer bookingsCreated;

    @Column(name = "bookings_completed", nullable = false)
    private Integer bookingsCompleted;

    @Column(name = "bookings_cancelled", nullable = false)
    private Integer bookingsCancelled;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;
}
