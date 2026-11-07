package com.bluecollar.category.repository;

import com.bluecollar.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Category> findByActiveTrue(Pageable pageable);

    java.util.List<Category> findByActiveTrue();

    Optional<Category> findByIdAndActiveTrue(UUID id);
}
