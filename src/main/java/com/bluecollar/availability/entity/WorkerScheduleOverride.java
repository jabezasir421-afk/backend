package com.bluecollar.availability.entity;

import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.worker.entity.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "worker_schedule_override")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkerScheduleOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    @Column(nullable = false)
    private Boolean available;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 200)
    private String reason;
}
