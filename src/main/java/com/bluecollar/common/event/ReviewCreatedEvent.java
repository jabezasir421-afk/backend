package com.bluecollar.common.event;

import java.util.UUID;

public record ReviewCreatedEvent(UUID reviewId, UUID workerUserId, short rating) {
}
