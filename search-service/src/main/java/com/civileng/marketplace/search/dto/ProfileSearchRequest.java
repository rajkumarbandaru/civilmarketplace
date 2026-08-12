package com.civileng.marketplace.search.dto;

import lombok.Data;

@Data
public class ProfileSearchRequest {
    private String q;
    private String role;
    private String city;
    private Double minRating;
    private Double minPrice;
    private Double maxPrice;
    private Boolean verifiedOnly;
    private Boolean availableOnly;
    private String sort;
    private int page = 0;
    private int size = 20;
}
