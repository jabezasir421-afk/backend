package com.bluecollar.worker.mapper;

import com.bluecollar.category.entity.Category;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Worker;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class WorkerMapper {

    public Worker toEntity(CreateWorkerRequest request, Category category, Set<Skill> skills) {
        return Worker.builder()
                .firstName(normalize(request.firstName()))
                .lastName(normalize(request.lastName()))
                .phoneNumber(normalize(request.phoneNumber()))
                .email(normalize(request.email()))
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .experienceYears(request.experienceYears())
                .bio(request.bio())
                .hourlyRate(request.hourlyRate())
                .available(false)
                .verified(false)
                .active(true)
                .category(category)
                .skills(new LinkedHashSet<>(skills))
                .build();
    }

    public WorkerResponse toResponse(Worker worker) {
        return new WorkerResponse(
                worker.getId(),
                worker.getFirstName(),
                worker.getLastName(),
                worker.getPhoneNumber(),
                worker.getEmail(),
                worker.getGender(),
                worker.getDateOfBirth(),
                worker.getExperienceYears(),
                worker.getBio(),
                worker.getHourlyRate(),
                worker.getAvailable(),
                worker.getVerified(),
                worker.getActive(),
                worker.getCategory().getId(),
                worker.getCategory().getName(),
                toSkillResponses(worker.getSkills()),
                worker.getAverageRating(),
                worker.getReviewCount(),
                worker.getCreatedAt(),
                worker.getUpdatedAt()
        );
    }

    public void updateEntity(Worker worker, UpdateWorkerRequest request, Category category, Set<Skill> skills) {
        worker.setFirstName(normalize(request.firstName()));
        worker.setLastName(normalize(request.lastName()));
        worker.setPhoneNumber(normalize(request.phoneNumber()));
        worker.setEmail(normalize(request.email()));
        worker.setGender(request.gender());
        worker.setDateOfBirth(request.dateOfBirth());
        worker.setExperienceYears(request.experienceYears());
        worker.setBio(request.bio());
        worker.setHourlyRate(request.hourlyRate());
        worker.setCategory(category);
        worker.setSkills(new LinkedHashSet<>(skills));
    }

    private List<SkillResponse> toSkillResponses(Set<Skill> skills) {
        return skills.stream()
                .sorted(Comparator.comparing(Skill::getName))
                .map(skill -> new SkillResponse(
                        skill.getId(),
                        skill.getName(),
                        skill.getDescription(),
                        skill.getActive(),
                        skill.getCreatedAt(),
                        skill.getUpdatedAt()
                ))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
