package com.bluecollar.analytics.repository;

import com.bluecollar.analytics.entity.AnalyticsWorkerRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AnalyticsWorkerRankingRepository extends JpaRepository<AnalyticsWorkerRanking, UUID> {

    List<AnalyticsWorkerRanking> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate);
}
