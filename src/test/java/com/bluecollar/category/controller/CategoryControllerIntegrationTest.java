package com.bluecollar.category.controller;

import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
class CategoryControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        categoryRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategoryShouldCreateCategoryAndReturnApiResponse() throws Exception {
        String payload = "{\"name\":\"Plumbing\",\"description\":\"Pipe services\"}";

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category created successfully"))
                .andExpect(jsonPath("$.data.name").value("Plumbing"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategoryShouldReturnValidationErrorsForInvalidPayload() throws Exception {
        String payload = "{\"name\":\"A\",\"description\":\"\"}";

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategoryShouldReturnConflictWhenNameAlreadyExists() throws Exception {
        categoryRepository.saveAndFlush(buildCategory("Plumbing", "Existing service"));

        String payload = "{\"name\":\"Plumbing\",\"description\":\"New description\"}";

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category with name 'Plumbing' already exists"));
    }

    @Test
    void getAllCategoriesShouldReturnPaginatedCategories() throws Exception {
        categoryRepository.saveAll(List.of(
                buildCategory("Plumbing", "Pipe services"),
                buildCategory("Electrical", "Electrical services")
        ));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Categories fetched successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getCategoryByIdShouldReturnCategory() throws Exception {
        Category category = categoryRepository.saveAndFlush(buildCategory("Plumbing", "Pipe services"));

        mockMvc.perform(get("/api/v1/categories/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(category.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Plumbing"));
    }

    @Test
    void getCategoryByIdShouldReturnNotFoundForMissingCategory() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/categories/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category with id '%s' was not found".formatted(missingId)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategoryShouldUpdateCategoryAndReturnApiResponse() throws Exception {
        Category category = categoryRepository.saveAndFlush(buildCategory("Plumbing", "Pipe services"));
        String payload = "{\"name\":\"Electrical\",\"description\":\"Electrical services\",\"active\":false}";

        mockMvc.perform(put("/api/v1/categories/{id}", category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Electrical"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategoryShouldDeleteExistingCategory() throws Exception {
        Category category = categoryRepository.saveAndFlush(buildCategory("Plumbing", "Pipe services"));

        mockMvc.perform(delete("/api/v1/categories/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Category deleted successfully"));

        assertFalse(categoryRepository.findById(category.getId()).isPresent());
    }

    private Category buildCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        category.setActive(true);
        return category;
    }
}
