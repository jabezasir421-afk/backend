package com.bluecollar.worker.dto;

import com.bluecollar.worker.entity.Gender;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UpdateWorkerRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        String phoneNumber,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        Gender gender,

        LocalDate dateOfBirth,

        @Min(value = 0, message = "Experience years must be greater than or equal to 0")
        Integer experienceYears,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        @Positive(message = "Hourly rate must be positive")
        BigDecimal hourlyRate,

        @NotNull(message = "Available status is required")
        Boolean available,

        @NotNull(message = "Verified status is required")
        Boolean verified,

        @NotNull(message = "Active status is required")
        Boolean active,

        @NotNull(message = "Category is required")
        UUID categoryId,

        Set<UUID> skillIds
) {
    public UpdateWorkerRequest {
        skillIds = skillIds == null
                ? Set.of()
                : Set.copyOf(skillIds);
    }
}
