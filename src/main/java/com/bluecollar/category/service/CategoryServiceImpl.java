package com.bluecollar.category.service;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.category.dto.CategoryResponse;
import com.bluecollar.category.dto.CreateCategoryRequest;
import com.bluecollar.category.dto.UpdateCategoryRequest;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryAlreadyExistsException;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.mapper.CategoryMapper;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.config.CacheConfig;
import com.bluecollar.common.security.SecurityUtils;
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
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @CacheEvict(value = {CacheConfig.CATEGORIES_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        validateNameAvailable(request.name());

        Category category = categoryMapper.toEntity(request);
        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> getAllCategories(Pageable pageable) {
        return categoryRepository.findByActiveTrue(pageable)
                .map(categoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        Category category = findCategory(id);
        if (!Boolean.TRUE.equals(category.getActive())) {
            boolean isAdmin = SecurityUtils.isAuthenticated()
                    && SecurityUtils.getCurrentUser().role() == UserRole.ADMIN;
            if (!isAdmin) {
                throw new CategoryNotFoundException(id);
            }
        }
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = {CacheConfig.CATEGORIES_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findCategory(id);
        validateNameAvailableForUpdate(category, request.name());

        categoryMapper.updateEntity(category, request);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @CacheEvict(value = {CacheConfig.CATEGORIES_CACHE, CacheConfig.WORKER_SEARCH_CACHE}, allEntries = true)
    public void deleteCategory(UUID id) {
        Category category = findCategory(id);
        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private void validateNameAvailable(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name.trim())) {
            throw new CategoryAlreadyExistsException(name);
        }
    }

    private void validateNameAvailableForUpdate(Category category, String name) {
        categoryRepository.findByNameIgnoreCase(name.trim())
                .filter(existingCategory -> !existingCategory.getId().equals(category.getId()))
                .ifPresent(existingCategory -> {
                    throw new CategoryAlreadyExistsException(name);
                });
    }
}
