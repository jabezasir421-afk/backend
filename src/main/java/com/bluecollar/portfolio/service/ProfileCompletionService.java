package com.bluecollar.portfolio.service;

import com.bluecollar.portfolio.dto.ProfileCompletionResponse;
import com.bluecollar.worker.entity.Worker;

public interface ProfileCompletionService {

    ProfileCompletionResponse calculate(Worker worker);

    void recalculateAndSave(Worker worker);
}
