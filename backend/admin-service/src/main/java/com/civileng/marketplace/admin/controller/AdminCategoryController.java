package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.dto.CategoryDTO;
import com.civileng.marketplace.admin.service.AdminCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Category Management", description = "Admin service category CRUD and management APIs")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping
    @Operation(summary = "Get all service categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        return ResponseEntity.ok(adminCategoryService.getCategories());
    }

    @PostMapping
    @Operation(summary = "Create a new service category")
    public ResponseEntity<Map<String, Object>> createCategory(
            @Valid @RequestBody CategoryDTO.CreateCategoryRequest request) {
        Map<String, Object> result = adminCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update an existing category")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryDTO.UpdateCategoryRequest request) {
        return ResponseEntity.ok(adminCategoryService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete a category")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(adminCategoryService.deleteCategory(categoryId));
    }

    @PutMapping("/{categoryId}/status")
    @Operation(summary = "Toggle category active status")
    public ResponseEntity<Map<String, Object>> toggleCategoryStatus(@PathVariable Long categoryId) {
        return ResponseEntity.ok(adminCategoryService.toggleCategoryStatus(categoryId));
    }
}
