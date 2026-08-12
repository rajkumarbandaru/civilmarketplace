package com.civileng.marketplace.admin.service;

import com.civileng.marketplace.admin.client.BookingServiceClient;
import com.civileng.marketplace.admin.dto.CategoryDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCategoryService {

    private final BookingServiceClient bookingServiceClient;

    @CircuitBreaker(name = "categoryService", fallbackMethod = "getCategoriesFallback")
    public Map<String, Object> getCategories() {
        try {
            Map<String, Object> response = bookingServiceClient.getAllCategories().getBody();
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch categories: {}", e.getMessage());
        }
        return getCategoriesFallback(new Exception("Fallback"));
    }

    public Map<String, Object> createCategory(CategoryDTO.CreateCategoryRequest request) {
        try {
            Map<String, Object> response = bookingServiceClient.createCategory(request).getBody();
            return response != null ? response : createSuccessResponse("Category created successfully");
        } catch (Exception e) {
            log.error("Failed to create category: {}", e.getMessage());
            return createErrorResponse("Failed to create category: " + e.getMessage());
        }
    }

    public Map<String, Object> updateCategory(Long categoryId, CategoryDTO.UpdateCategoryRequest request) {
        try {
            Map<String, Object> response = bookingServiceClient.updateCategory(categoryId, request).getBody();
            return response != null ? response : createSuccessResponse("Category updated successfully");
        } catch (Exception e) {
            log.error("Failed to update category {}: {}", categoryId, e.getMessage());
            return createErrorResponse("Failed to update category: " + e.getMessage());
        }
    }

    public Map<String, Object> deleteCategory(Long categoryId) {
        try {
            Map<String, Object> response = bookingServiceClient.deleteCategory(categoryId).getBody();
            return response != null ? response : createSuccessResponse("Category deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete category {}: {}", categoryId, e.getMessage());
            return createErrorResponse("Failed to delete category: " + e.getMessage());
        }
    }

    public Map<String, Object> toggleCategoryStatus(Long categoryId) {
        try {
            Map<String, Object> response = bookingServiceClient.toggleCategoryStatus(categoryId).getBody();
            return response != null ? response : createSuccessResponse("Category status toggled successfully");
        } catch (Exception e) {
            log.error("Failed to toggle category status {}: {}", categoryId, e.getMessage());
            return createErrorResponse("Failed to toggle category status: " + e.getMessage());
        }
    }

    private Map<String, Object> getCategoriesFallback(Throwable t) {
        List<Map<String, Object>> categories = new ArrayList<>();
        String[][] catData = {
                {"1", "House Planning", "house-planning", "12"},
                {"2", "Architecture", "architecture", "8"},
                {"3", "Structural Engineering", "structural-engineering", "15"},
                {"4", "Survey Services", "survey-services", "6"},
                {"5", "Interior Design", "interior-design", "10"},
                {"6", "Construction", "construction", "20"},
                {"7", "Electrical", "electrical", "5"},
                {"8", "Plumbing", "plumbing", "4"}
        };
        for (String[] cat : catData) {
            categories.add(Map.of(
                    "id", Long.parseLong(cat[0]),
                    "name", cat[1],
                    "slug", cat[2],
                    "sortOrder", Integer.parseInt(cat[0]),
                    "active", true,
                    "servicesCount", Integer.parseInt(cat[3])
            ));
        }
        return Map.of("success", true, "data", categories);
    }

    private Map<String, Object> createSuccessResponse(String message) {
        return Map.of("success", true, "message", message);
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of("success", false, "message", message);
    }
}
