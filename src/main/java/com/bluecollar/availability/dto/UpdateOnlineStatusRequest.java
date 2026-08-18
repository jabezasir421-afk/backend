package com.bluecollar.availability.dto;

import com.bluecollar.worker.entity.OnlineStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOnlineStatusRequest(
        @NotNull OnlineStatus status
) {
}
