package com.civileng.marketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String status;
    private String city;
    private String profilePicture;
    private boolean emailVerified;
    private boolean phoneVerified;
    private int bookings;
    private double rating;
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserRequest {
        private String name;
        private String email;
        private String phone;
        private String role;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private String status;
        private String reason;
    }
}
