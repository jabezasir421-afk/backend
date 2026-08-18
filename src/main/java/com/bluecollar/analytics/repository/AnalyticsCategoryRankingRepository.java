package com.bluecollar.analytics.repository;

import com.bluecollar.analytics.entity.AnalyticsCategoryRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AnalyticsCategoryRankingRepository extends JpaRepository<AnalyticsCategoryRanking, UUID> {

    List<AnalyticsCategoryRanking> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate);
}
