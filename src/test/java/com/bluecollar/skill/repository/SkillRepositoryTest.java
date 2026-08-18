package com.bluecollar.skill.repository;

import com.bluecollar.skill.entity.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SkillRepositoryTest {

    @Autowired
    private SkillRepository skillRepository;

    @Test
    void existsByNameIgnoreCaseShouldReturnTrueWhenSkillExistsIgnoringCase() {
        Skill skill = Skill.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build();

        skillRepository.saveAndFlush(skill);

        boolean exists = skillRepository.existsByNameIgnoreCase("PLUMBING");

        assertTrue(exists);
    }

    @Test
    void findByNameIgnoreCaseShouldReturnSkillWhenNameMatchesIgnoringCase() {
        Skill skill = Skill.builder()
                .name("Electrical")
                .description("Electrical services")
                .active(true)
                .build();

        skillRepository.saveAndFlush(skill);

        Optional<Skill> foundSkill = skillRepository.findByNameIgnoreCase("electrical");

        assertTrue(foundSkill.isPresent());
        assertEquals("Electrical", foundSkill.get().getName());
    }
}
