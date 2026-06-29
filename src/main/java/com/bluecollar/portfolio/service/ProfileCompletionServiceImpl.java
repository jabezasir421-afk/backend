package com.bluecollar.portfolio.service;

import com.bluecollar.portfolio.dto.ProfileCompletionResponse;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileCompletionServiceImpl implements ProfileCompletionService {

    private final WorkerPortfolioItemRepository portfolioItemRepository;
    private final WorkerCertificateRepository certificateRepository;
    private final WorkerIdentityDocumentRepository identityDocumentRepository;
    private final WorkerRepository workerRepository;

    @Override
    @Transactional(readOnly = true)
    public ProfileCompletionResponse calculate(Worker worker) {
        Map<String, Boolean> sections = new LinkedHashMap<>();
        List<String> missingFields = new ArrayList<>();

        boolean basicInfo = evaluateBasicInfo(worker, missingFields);
        sections.put("basicInfo", basicInfo);

        boolean bioAndRate = evaluateBioAndRate(worker, missingFields);
        sections.put("bioAndRate", bioAndRate);

        boolean skills = worker.getSkills() != null && !worker.getSkills().isEmpty();
        sections.put("skills", skills);
        if (!skills) {
            missingFields.add("skills");
        }

        boolean profilePhoto = worker.getProfilePhotoFileId() != null;
        sections.put("profilePhoto", profilePhoto);
        if (!profilePhoto) {
            missingFields.add("profilePhoto");
        }

        long portfolioCount = portfolioItemRepository.countByWorkerIdAndActiveTrue(worker.getId());
        boolean portfolio = portfolioCount >= 3;
        sections.put("portfolio", portfolio);
        if (!portfolio) {
            missingFields.add("portfolioImages");
        }

        long certificateCount = certificateRepository.countByWorkerIdAndActiveTrue(worker.getId());
        boolean certificate = certificateCount >= 1;
        sections.put("certificate", certificate);
        if (!certificate) {
            missingFields.add("certificate");
        }

        long identityCount = identityDocumentRepository.countByWorkerIdAndActiveTrueAndVerificationStatusIn(
                worker.getId(),
                List.of(VerificationStatus.PENDING, VerificationStatus.VERIFIED)
        );
        boolean identity = identityCount >= 1;
        sections.put("identity", identity);
        if (!identity) {
            missingFields.add("identityDocument");
        }

        int completionPercent = 0;
        if (basicInfo) {
            completionPercent += 15;
        }
        if (bioAndRate) {
            completionPercent += 10;
        }
        if (skills) {
            completionPercent += 10;
        }
        if (profilePhoto) {
            completionPercent += 10;
        }
        if (portfolio) {
            completionPercent += 20;
        }
        if (certificate) {
            completionPercent += 15;
        }
        if (identity) {
            completionPercent += 20;
        }

        return new ProfileCompletionResponse(completionPercent, sections, missingFields);
    }

    @Override
    public void recalculateAndSave(Worker worker) {
        ProfileCompletionResponse completion = calculate(worker);
        worker.setProfileCompletionPercent((short) completion.completionPercent());
        workerRepository.save(worker);
    }

    private boolean evaluateBasicInfo(Worker worker, List<String> missingFields) {
        boolean complete = isPresent(worker.getFirstName())
                && isPresent(worker.getLastName())
                && isPresent(worker.getPhoneNumber())
                && isPresent(worker.getEmail())
                && isPresent(worker.getPrimaryCity());
        if (!isPresent(worker.getFirstName())) {
            missingFields.add("firstName");
        }
        if (!isPresent(worker.getLastName())) {
            missingFields.add("lastName");
        }
        if (!isPresent(worker.getPhoneNumber())) {
            missingFields.add("phoneNumber");
        }
        if (!isPresent(worker.getEmail())) {
            missingFields.add("email");
        }
        if (!isPresent(worker.getPrimaryCity())) {
            missingFields.add("primaryCity");
        }
        return complete;
    }

    private boolean evaluateBioAndRate(Worker worker, List<String> missingFields) {
        boolean bioComplete = worker.getBio() != null && worker.getBio().trim().length() >= 50;
        boolean rateComplete = worker.getHourlyRate() != null;
        if (!bioComplete) {
            missingFields.add("bio");
        }
        if (!rateComplete) {
            missingFields.add("hourlyRate");
        }
        return bioComplete && rateComplete;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
