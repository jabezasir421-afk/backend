package com.bluecollar.analytics.entity;

import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.worker.entity.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "analytics_worker_ranking")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsWorkerRanking extends BaseEntity {

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "completed_bookings", nullable = false)
    private Integer completedBookings;

    @Column(nullable = false)
    private Short rank;
}
