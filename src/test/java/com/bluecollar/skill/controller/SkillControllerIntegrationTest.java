package com.bluecollar.skill.controller;

import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SkillControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private SkillRepository skillRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        skillRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSkillShouldCreateSkillAndReturnApiResponse() throws Exception {
        String payload = "{\"name\":\"Plumbing\",\"description\":\"Pipe services\"}";

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Skill created successfully"))
                .andExpect(jsonPath("$.data.name").value("Plumbing"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSkillShouldReturnValidationErrorsForInvalidPayload() throws Exception {
        String payload = "{\"name\":\"A\",\"description\":\"\"}";

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSkillShouldReturnConflictWhenNameAlreadyExists() throws Exception {
        skillRepository.saveAndFlush(buildSkill("Plumbing", "Existing service"));

        String payload = "{\"name\":\"Plumbing\",\"description\":\"New description\"}";

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Skill with name 'Plumbing' already exists"));
    }

    @Test
    void getAllSkillsShouldReturnPaginatedSkills() throws Exception {
        skillRepository.saveAll(List.of(
                buildSkill("Plumbing", "Pipe services"),
                buildSkill("Electrical", "Electrical services")
        ));

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Skills fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getSkillByIdShouldReturnSkill() throws Exception {
        Skill skill = skillRepository.saveAndFlush(buildSkill("Plumbing", "Pipe services"));

        mockMvc.perform(get("/api/v1/skills/{id}", skill.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(skill.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Plumbing"));
    }

    @Test
    void getSkillByIdShouldReturnNotFoundForMissingSkill() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/skills/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Skill with id '%s' was not found".formatted(missingId)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSkillShouldUpdateSkillAndReturnApiResponse() throws Exception {
        Skill skill = skillRepository.saveAndFlush(buildSkill("Plumbing", "Pipe services"));
        String payload = "{\"name\":\"Electrical\",\"description\":\"Electrical services\",\"active\":false}";

        mockMvc.perform(put("/api/v1/skills/{id}", skill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Skill updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Electrical"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSkillShouldDeleteExistingSkill() throws Exception {
        Skill skill = skillRepository.saveAndFlush(buildSkill("Plumbing", "Pipe services"));

        mockMvc.perform(delete("/api/v1/skills/{id}", skill.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Skill deleted successfully"));

        assertFalse(skillRepository.findById(skill.getId()).isPresent());
    }

    private Skill buildSkill(String name, String description) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setActive(true);
        return skill;
    }
}
