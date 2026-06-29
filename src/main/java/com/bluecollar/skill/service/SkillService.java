package com.bluecollar.skill.service;

import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.dto.UpdateSkillRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SkillService {

    /**
     * Creates a new skill when the requested name is not already in use.
     */
    SkillResponse createSkill(CreateSkillRequest request);

    /**
     * Returns all skills ordered by creation time.
     */
    Page<SkillResponse> getAllSkills(Pageable pageable);

    /**
     * Returns a skill by its unique identifier.
     */
    SkillResponse getSkillById(UUID id);

    /**
     * Updates a skill when it exists and the requested name does not conflict.
     */
    SkillResponse updateSkill(UUID id, UpdateSkillRequest request);

    /**
     * Deletes a skill by its unique identifier.
     */
    void deleteSkill(UUID id);
}
