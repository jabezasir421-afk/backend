package com.bluecollar.availability.repository;

import com.bluecollar.availability.entity.WorkerWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerWorkingHoursRepository extends JpaRepository<WorkerWorkingHours, UUID> {

    List<WorkerWorkingHours> findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(UUID workerId);

    Optional<WorkerWorkingHours> findByWorkerIdAndDayOfWeekAndActiveTrue(UUID workerId, Short dayOfWeek);

    void deleteByWorkerId(UUID workerId);
}
