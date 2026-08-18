package com.bluecollar.portfolio.repository;

import com.bluecollar.portfolio.entity.WorkerPortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerPortfolioItemRepository extends JpaRepository<WorkerPortfolioItem, UUID> {

    long countByWorkerIdAndActiveTrue(UUID workerId);

    List<WorkerPortfolioItem> findByWorkerIdAndActiveTrueOrderByDisplayOrderAsc(UUID workerId);

    Optional<WorkerPortfolioItem> findByIdAndWorkerIdAndActiveTrue(UUID id, UUID workerId);
}
