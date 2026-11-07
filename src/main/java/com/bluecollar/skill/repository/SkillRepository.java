package com.bluecollar.skill.repository;

import com.bluecollar.skill.entity.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Skill> findByActiveTrue(Pageable pageable);

    Optional<Skill> findByIdAndActiveTrue(UUID id);
}
