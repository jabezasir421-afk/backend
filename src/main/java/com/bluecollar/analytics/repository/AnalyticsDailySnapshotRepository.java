package com.bluecollar.analytics.repository;

import com.bluecollar.analytics.entity.AnalyticsDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsDailySnapshotRepository extends JpaRepository<AnalyticsDailySnapshot, UUID> {

    Optional<AnalyticsDailySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    List<AnalyticsDailySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate from, LocalDate to);
}
