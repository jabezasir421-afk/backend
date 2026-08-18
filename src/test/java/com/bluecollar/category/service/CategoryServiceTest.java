package com.bluecollar.category.service;

import com.bluecollar.category.dto.CategoryResponse;
import com.bluecollar.category.dto.CreateCategoryRequest;
import com.bluecollar.category.dto.UpdateCategoryRequest;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryAlreadyExistsException;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.mapper.CategoryMapper;
import com.bluecollar.category.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CreateCategoryRequest createRequest;
    private UpdateCategoryRequest updateRequest;
    private Category category;
    private CategoryResponse response;

    @BeforeEach
    void setUp() {
        createRequest = new CreateCategoryRequest("Plumbing", "Pipe services");
        updateRequest = new UpdateCategoryRequest("Electrical", "Electrical services", true);
        category = new Category();
        category.setName("Plumbing");
        category.setDescription("Pipe services");
        category.setActive(true);
        response = new CategoryResponse(UUID.randomUUID(), "Plumbing", "Pipe services", true, null, null);
    }

    @Test
    void createCategoryShouldCreateAndReturnResponseWhenNameIsAvailable() {
        Category savedCategory = new Category();
        savedCategory.setName("Plumbing");
        savedCategory.setDescription("Pipe services");
        savedCategory.setActive(true);

        when(categoryRepository.existsByNameIgnoreCase("Plumbing")).thenReturn(false);
        when(categoryMapper.toEntity(createRequest)).thenReturn(category);
        when(categoryRepository.save(category)).thenReturn(savedCategory);
        when(categoryMapper.toResponse(savedCategory)).thenReturn(response);

        CategoryResponse result = categoryService.createCategory(createRequest);

        assertEquals(response, result);
        verify(categoryRepository).existsByNameIgnoreCase("Plumbing");
        verify(categoryRepository).save(category);
    }

    @Test
    void createCategoryShouldThrowWhenNameAlreadyExists() {
        when(categoryRepository.existsByNameIgnoreCase("Plumbing")).thenReturn(true);

        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.createCategory(createRequest));
        verify(categoryRepository).existsByNameIgnoreCase("Plumbing");
        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getCategoryByIdShouldReturnCategoryWhenItExists() {
        UUID id = UUID.randomUUID();
        category.setId(id);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(response);

        CategoryResponse result = categoryService.getCategoryById(id);

        assertEquals(response, result);
        verify(categoryRepository).findById(id);
    }

    @Test
    void getCategoryByIdShouldThrowWhenCategoryDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> categoryService.getCategoryById(id));
        verify(categoryRepository).findById(id);
    }

    @Test
    void getAllCategoriesShouldReturnPageOfResponses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Category> page = new PageImpl<>(List.of(category), pageable, 1);

        when(categoryRepository.findAll(pageable)).thenReturn(page);
        when(categoryMapper.toResponse(category)).thenReturn(response);

        Page<CategoryResponse> result = categoryService.getAllCategories(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().getFirst());
        verify(categoryRepository).findAll(pageable);
    }

    @Test
    void updateCategoryShouldUpdateAndReturnResponseWhenCategoryExists() {
        UUID id = UUID.randomUUID();
        category.setId(id);
        Category updatedCategory = new Category();
        updatedCategory.setId(id);
        updatedCategory.setName("Electrical");
        updatedCategory.setDescription("Electrical services");
        updatedCategory.setActive(true);
        CategoryResponse updatedResponse = new CategoryResponse(id, "Electrical", "Electrical services", true, null, null);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Electrical")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(updatedCategory);
        when(categoryMapper.toResponse(updatedCategory)).thenReturn(updatedResponse);

        CategoryResponse result = categoryService.updateCategory(id, updateRequest);

        assertEquals(updatedResponse, result);
        verify(categoryRepository).findById(id);
        verify(categoryRepository).findByNameIgnoreCase("Electrical");
        verify(categoryRepository).save(category);
    }

    @Test
    void deleteCategoryShouldDeleteWhenCategoryExists() {
        UUID id = UUID.randomUUID();

        when(categoryRepository.existsById(id)).thenReturn(true);

        categoryService.deleteCategory(id);

        verify(categoryRepository).existsById(id);
        verify(categoryRepository).deleteById(id);
    }
}
