package com.bluecollar.availability.repository;

import com.bluecollar.availability.entity.WorkerScheduleOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerScheduleOverrideRepository extends JpaRepository<WorkerScheduleOverride, UUID> {

    List<WorkerScheduleOverride> findByWorkerIdOrderByOverrideDateAsc(UUID workerId);

    long countByWorkerId(UUID workerId);

    Optional<WorkerScheduleOverride> findByIdAndWorkerId(UUID id, UUID workerId);

    Optional<WorkerScheduleOverride> findByWorkerIdAndOverrideDate(UUID workerId, LocalDate overrideDate);
}
