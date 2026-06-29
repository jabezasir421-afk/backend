package com.bluecollar.category.repository;

import com.bluecollar.category.entity.Category;
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
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void existsByNameIgnoreCaseShouldReturnTrueWhenCategoryExistsIgnoringCase() {
        Category category = Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build();

        categoryRepository.saveAndFlush(category);

        boolean exists = categoryRepository.existsByNameIgnoreCase("PLUMBING");

        assertTrue(exists);
    }

    @Test
    void findByNameIgnoreCaseShouldReturnCategoryWhenNameMatchesIgnoringCase() {
        Category category = Category.builder()
                .name("Electrical")
                .description("Electrical services")
                .active(true)
                .build();

        categoryRepository.saveAndFlush(category);

        Optional<Category> foundCategory = categoryRepository.findByNameIgnoreCase("electrical");

        assertTrue(foundCategory.isPresent());
        assertEquals("Electrical", foundCategory.get().getName());
    }
}
