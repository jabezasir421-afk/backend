package com.bluecollar.category.service;

import com.bluecollar.category.dto.CategoryResponse;
import com.bluecollar.category.dto.CreateCategoryRequest;
import com.bluecollar.category.dto.UpdateCategoryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CategoryService {

    /**
     * Creates a new category when the requested name is not already in use.
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Returns all categories ordered by creation time.
     */
    Page<CategoryResponse> getAllCategories(Pageable pageable);

    /**
     * Returns a category by its unique identifier.
     */
    CategoryResponse getCategoryById(UUID id);

    /**
     * Updates a category when it exists and the requested name does not conflict.
     */
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request);

    /**
     * Deletes a category by its unique identifier.
     */
    void deleteCategory(UUID id);
}
