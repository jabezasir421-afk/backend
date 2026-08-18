package com.bluecollar.skill.service;

import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.dto.UpdateSkillRequest;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.exception.SkillAlreadyExistsException;
import com.bluecollar.skill.exception.SkillNotFoundException;
import com.bluecollar.skill.mapper.SkillMapper;
import com.bluecollar.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillMapper skillMapper;

    @InjectMocks
    private SkillServiceImpl skillService;

    private CreateSkillRequest createRequest;
    private UpdateSkillRequest updateRequest;
    private Skill skill;
    private SkillResponse response;

    @BeforeEach
    void setUp() {
        createRequest = new CreateSkillRequest("Plumbing", "Pipe services");
        updateRequest = new UpdateSkillRequest("Electrical", "Electrical services", true);
        skill = new Skill();
        skill.setName("Plumbing");
        skill.setDescription("Pipe services");
        skill.setActive(true);
        response = new SkillResponse(UUID.randomUUID(), "Plumbing", "Pipe services", true, null, null);
    }

    @Test
    void createSkillShouldCreateAndReturnResponseWhenNameIsAvailable() {
        Skill savedSkill = new Skill();
        savedSkill.setName("Plumbing");
        savedSkill.setDescription("Pipe services");
        savedSkill.setActive(true);

        when(skillRepository.existsByNameIgnoreCase("Plumbing")).thenReturn(false);
        when(skillMapper.toEntity(createRequest)).thenReturn(skill);
        when(skillRepository.save(skill)).thenReturn(savedSkill);
        when(skillMapper.toResponse(savedSkill)).thenReturn(response);

        SkillResponse result = skillService.createSkill(createRequest);

        assertEquals(response, result);
        verify(skillRepository).existsByNameIgnoreCase("Plumbing");
        verify(skillRepository).save(skill);
    }

    @Test
    void createSkillShouldThrowWhenNameAlreadyExists() {
        when(skillRepository.existsByNameIgnoreCase("Plumbing")).thenReturn(true);

        assertThrows(SkillAlreadyExistsException.class, () -> skillService.createSkill(createRequest));
        verify(skillRepository).existsByNameIgnoreCase("Plumbing");
        verify(skillRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getSkillByIdShouldReturnSkillWhenItExists() {
        UUID id = UUID.randomUUID();
        skill.setId(id);

        when(skillRepository.findById(id)).thenReturn(Optional.of(skill));
        when(skillMapper.toResponse(skill)).thenReturn(response);

        SkillResponse result = skillService.getSkillById(id);

        assertEquals(response, result);
        verify(skillRepository).findById(id);
    }

    @Test
    void getSkillByIdShouldThrowWhenSkillDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(skillRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(SkillNotFoundException.class, () -> skillService.getSkillById(id));
        verify(skillRepository).findById(id);
    }

    @Test
    void getAllSkillsShouldReturnPageOfResponses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Skill> page = new PageImpl<>(List.of(skill), pageable, 1);

        when(skillRepository.findAll(pageable)).thenReturn(page);
        when(skillMapper.toResponse(skill)).thenReturn(response);

        Page<SkillResponse> result = skillService.getAllSkills(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().getFirst());
        verify(skillRepository).findAll(pageable);
    }

    @Test
    void updateSkillShouldUpdateAndReturnResponseWhenSkillExists() {
        UUID id = UUID.randomUUID();
        skill.setId(id);
        Skill updatedSkill = new Skill();
        updatedSkill.setId(id);
        updatedSkill.setName("Electrical");
        updatedSkill.setDescription("Electrical services");
        updatedSkill.setActive(true);
        SkillResponse updatedResponse = new SkillResponse(id, "Electrical", "Electrical services", true, null, null);

        when(skillRepository.findById(id)).thenReturn(Optional.of(skill));
        when(skillRepository.findByNameIgnoreCase("Electrical")).thenReturn(Optional.empty());
        when(skillRepository.save(skill)).thenReturn(updatedSkill);
        when(skillMapper.toResponse(updatedSkill)).thenReturn(updatedResponse);

        SkillResponse result = skillService.updateSkill(id, updateRequest);

        assertEquals(updatedResponse, result);
        verify(skillRepository).findById(id);
        verify(skillRepository).findByNameIgnoreCase("Electrical");
        verify(skillRepository).save(skill);
    }

    @Test
    void deleteSkillShouldDeleteWhenSkillExists() {
        UUID id = UUID.randomUUID();

        when(skillRepository.existsById(id)).thenReturn(true);

        skillService.deleteSkill(id);

        verify(skillRepository).existsById(id);
        verify(skillRepository).deleteById(id);
    }
}
