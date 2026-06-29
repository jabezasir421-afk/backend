package com.bluecollar.worker.dto;

import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.worker.entity.Gender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkerResponse(
        UUID id,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        Gender gender,
        LocalDate dateOfBirth,
        Integer experienceYears,
        String bio,
        BigDecimal hourlyRate,
        Boolean available,
        Boolean verified,
        Boolean active,
        UUID categoryId,
        String categoryName,
        List<SkillResponse> skills,
        BigDecimal averageRating,
        Integer reviewCount,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkerResponse {
        skills = skills == null
                ? List.of()
                : List.copyOf(skills);
    }
}
