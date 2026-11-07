package com.bluecollar.booking.repository;

import com.bluecollar.worker.entity.Worker;

public interface WorkerRankingProjection {
    Worker getWorker();
    long getBookingCount();
}
