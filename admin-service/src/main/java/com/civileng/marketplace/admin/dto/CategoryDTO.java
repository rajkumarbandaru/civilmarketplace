package com.civileng.marketplace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String icon;
    private String image;
    private Long parentId;
    private String parentName;
    private int sortOrder;
    private boolean active;
    private long servicesCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCategoryRequest {
        @NotBlank(message = "Category name is required")
        private String name;

        @NotBlank(message = "Slug is required")
        private String slug;

        private String description;
        private String icon;
        private String image;
        private Long parentId;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryRequest {
        private String name;
        private String slug;
        private String description;
        private String icon;
        private String image;
        private Long parentId;
        private Integer sortOrder;
        private Boolean active;
    }
}
