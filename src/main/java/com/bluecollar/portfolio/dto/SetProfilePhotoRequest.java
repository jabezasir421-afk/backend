package com.bluecollar.portfolio.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetProfilePhotoRequest(
        @NotNull UUID fileId
) {
}
