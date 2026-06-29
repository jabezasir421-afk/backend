package com.bluecollar.skill.mapper;

import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.dto.UpdateSkillRequest;
import com.bluecollar.skill.entity.Skill;
import org.springframework.stereotype.Component;

@Component
public class SkillMapper {

    public Skill toEntity(CreateSkillRequest request) {
        return Skill.builder()
                .name(normalizeName(request.name()))
                .description(request.description())
                .active(Boolean.TRUE)
                .build();
    }

    public SkillResponse toResponse(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getActive(),
                skill.getCreatedAt(),
                skill.getUpdatedAt()
        );
    }

    public void updateEntity(Skill skill, UpdateSkillRequest request) {
        skill.setName(normalizeName(request.name()));
        skill.setDescription(request.description());
        skill.setActive(request.active());
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }
}
