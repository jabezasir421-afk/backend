package com.bluecollar.search.repository;

import com.bluecollar.search.entity.WorkerServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkerServiceAreaRepository extends JpaRepository<WorkerServiceArea, UUID> {
}
