package com.bluecollar.category.mapper;

import com.bluecollar.category.dto.CategoryResponse;
import com.bluecollar.category.dto.CreateCategoryRequest;
import com.bluecollar.category.dto.UpdateCategoryRequest;
import com.bluecollar.category.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toEntity(CreateCategoryRequest request) {
        return Category.builder()
                .name(normalizeName(request.name()))
                .description(request.description())
                .active(Boolean.TRUE)
                .build();
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public void updateEntity(Category category, UpdateCategoryRequest request) {
        category.setName(normalizeName(request.name()));
        category.setDescription(request.description());
        category.setActive(request.active());
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }
}
