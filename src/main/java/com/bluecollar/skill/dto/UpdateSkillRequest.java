package com.bluecollar.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSkillRequest(
        @NotBlank(message = "Skill name is required")
        @Size(min = 2, max = 100, message = "Skill name must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotNull(message = "Active status is required")
        Boolean active
) {
}
