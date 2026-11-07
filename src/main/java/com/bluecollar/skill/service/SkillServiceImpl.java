package com.bluecollar.skill.service;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.config.CacheConfig;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.dto.UpdateSkillRequest;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.exception.SkillAlreadyExistsException;
import com.bluecollar.skill.exception.SkillNotFoundException;
import com.bluecollar.skill.mapper.SkillMapper;
import com.bluecollar.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;

    @Override
    @CacheEvict(value = {CacheConfig.SKILLS_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public SkillResponse createSkill(CreateSkillRequest request) {
        validateNameAvailable(request.name());

        Skill skill = skillMapper.toEntity(request);
        Skill savedSkill = skillRepository.save(skill);
        return skillMapper.toResponse(savedSkill);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SkillResponse> getAllSkills(Pageable pageable) {
        return skillRepository.findByActiveTrue(pageable)
                .map(skillMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillResponse getSkillById(UUID id) {
        Skill skill = findSkill(id);
        if (!Boolean.TRUE.equals(skill.getActive())) {
            boolean isAdmin = SecurityUtils.isAuthenticated()
                    && SecurityUtils.getCurrentUser().role() == UserRole.ADMIN;
            if (!isAdmin) {
                throw new SkillNotFoundException(id);
            }
        }
        return skillMapper.toResponse(skill);
    }

    @Override
    @CacheEvict(value = {CacheConfig.SKILLS_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public SkillResponse updateSkill(UUID id, UpdateSkillRequest request) {
        Skill skill = findSkill(id);
        validateNameAvailableForUpdate(skill, request.name());

        skillMapper.updateEntity(skill, request);
        return skillMapper.toResponse(skillRepository.save(skill));
    }

    @Override
    @CacheEvict(value = {CacheConfig.SKILLS_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public void deleteSkill(UUID id) {
        Skill skill = findSkill(id);
        skill.setActive(false);
        skillRepository.save(skill);
    }

    private Skill findSkill(UUID id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException(id));
    }

    private void validateNameAvailable(String name) {
        if (skillRepository.existsByNameIgnoreCase(name.trim())) {
            throw new SkillAlreadyExistsException(name);
        }
    }

    private void validateNameAvailableForUpdate(Skill skill, String name) {
        skillRepository.findByNameIgnoreCase(name.trim())
                .filter(existingSkill -> !existingSkill.getId().equals(skill.getId()))
                .ifPresent(existingSkill -> {
                    throw new SkillAlreadyExistsException(name);
                });
    }
}
