package com.bluecollar.analytics.dto;

import java.util.UUID;

public record TopWorkerResponse(UUID workerId, String fullName, long completedBookings, int rank) {
}
