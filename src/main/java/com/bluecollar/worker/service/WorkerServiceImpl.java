package com.bluecollar.worker.service;

import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.exception.SkillNotFoundException;
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerAlreadyExistsException;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkerServiceImpl implements WorkerService {

    private final WorkerRepository workerRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final WorkerMapper workerMapper;

    @Override
    public WorkerResponse createWorker(CreateWorkerRequest request) {
        validatePhoneNumberAvailable(request.phoneNumber());
        validateEmailAvailable(request.email());

        Category category = findCategory(request.categoryId());
        Set<Skill> skills = findSkills(request.skillIds());
        Worker worker = workerMapper.toEntity(request, category, skills);
        Worker savedWorker = workerRepository.save(worker);
        return workerMapper.toResponse(savedWorker);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkerResponse> getAllWorkers(Pageable pageable) {
        return workerRepository.findAll(pageable)
                .map(workerMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerResponse getWorkerById(UUID id) {
        return workerMapper.toResponse(findWorker(id));
    }

    @Override
    public WorkerResponse updateWorker(UUID id, UpdateWorkerRequest request) {
        Worker worker = findWorker(id);
        validatePhoneNumberAvailableForUpdate(worker, request.phoneNumber());
        validateEmailAvailableForUpdate(worker, request.email());

        Category category = findCategory(request.categoryId());
        Set<Skill> skills = findSkills(request.skillIds());
        workerMapper.updateEntity(worker, request, category, skills);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public void deleteWorker(UUID id) {
        if (!workerRepository.existsById(id)) {
            throw new WorkerNotFoundException(id);
        }
        workerRepository.deleteById(id);
    }

    private Worker findWorker(UUID id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new WorkerNotFoundException(id));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private Set<Skill> findSkills(Set<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> requestedSkillIds = new LinkedHashSet<>(skillIds);
        List<Skill> skills = skillRepository.findAllById(requestedSkillIds);
        if (skills.size() != requestedSkillIds.size()) {
            throw new SkillNotFoundException(findMissingSkillId(requestedSkillIds, skills));
        }
        return new LinkedHashSet<>(skills);
    }

    private UUID findMissingSkillId(Set<UUID> requestedSkillIds, List<Skill> skills) {
        Set<UUID> foundSkillIds = skills.stream()
                .map(Skill::getId)
                .collect(java.util.stream.Collectors.toSet());

        return requestedSkillIds.stream()
                .filter(skillId -> !foundSkillIds.contains(skillId))
                .findFirst()
                .orElseThrow();
    }

    private void validatePhoneNumberAvailable(String phoneNumber) {
        if (workerRepository.existsByPhoneNumber(phoneNumber.trim())) {
            throw new WorkerAlreadyExistsException("phone number", phoneNumber);
        }
    }

    private void validatePhoneNumberAvailableForUpdate(Worker worker, String phoneNumber) {
        workerRepository.findByPhoneNumber(phoneNumber.trim())
                .filter(existingWorker -> !existingWorker.getId().equals(worker.getId()))
                .ifPresent(existingWorker -> {
                    throw new WorkerAlreadyExistsException("phone number", phoneNumber);
                });
    }

    private void validateEmailAvailable(String email) {
        if (hasText(email) && workerRepository.existsByEmailIgnoreCase(email.trim())) {
            throw new WorkerAlreadyExistsException("email", email);
        }
    }

    private void validateEmailAvailableForUpdate(Worker worker, String email) {
        if (!hasText(email)) {
            return;
        }

        workerRepository.findByEmailIgnoreCase(email.trim())
                .filter(existingWorker -> !existingWorker.getId().equals(worker.getId()))
                .ifPresent(existingWorker -> {
                    throw new WorkerAlreadyExistsException("email", email);
                });
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
