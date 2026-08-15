package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
// The real AccountIdentifiers, not a mock: these tests assert on the stored email/phone,
// and a mock would return null and quietly hide the normalisation the controller now does.
@Import({com.civileng.marketplace.auth.exception.GlobalExceptionHandler.class,
        com.civileng.marketplace.auth.service.AccountIdentifiers.class})
@DisplayName("AdminUserController - admin endpoints for user management")
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.fail-fast=false",
    "spring.config.import=",
    "eureka.client.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    private Role adminRole;
    private Role customerRole;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().id(1L).name("ADMIN").description("Administrator").build();
        customerRole = Role.builder().id(2L).name("CUSTOMER").description("Customer").build();

        sampleUser = User.builder()
                .id(42L)
                .name("Rahul Sharma")
                .email("rahul@example.com")
                .phone("+91-9876543210")
                .passwordHash("$2a$10$hashed")
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2024, 6, 1, 14, 0))
                .lastLoginAt(LocalDateTime.of(2024, 6, 10, 9, 15))
                .build();
    }

    // ===== GET /admin/users =====

    @Nested
    @DisplayName("GET /admin/users - list users")
    class GetAllUsers {

        @Test
        @DisplayName("Returns paginated users with no filters")
        void getAllUsers_NoFilters_ReturnsPage() throws Exception {
            Page<User> page = new PageImpl<>(List.of(sampleUser));
            when(userRepository.findAdminUsers(isNull(), isNull(), isNull(), any(PageRequest.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/auth/admin/users")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(42))
                    .andExpect(jsonPath("$.data[0].name").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.data[0].email").value("rahul@example.com"))
                    .andExpect(jsonPath("$.data[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        @DisplayName("Forwards search and role/status filters to repository")
        void getAllUsers_WithFilters_DelegatesToRepository() throws Exception {
            Page<User> emptyPage = Page.empty();
            when(userRepository.findAdminUsers(eq("john"), eq("CUSTOMER"), eq(UserStatus.ACTIVE), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/auth/admin/users")
                            .param("page", "0")
                            .param("size", "10")
                            .param("search", "john")
                            .param("role", "CUSTOMER")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(userRepository).findAdminUsers(eq("john"), eq("CUSTOMER"), eq(UserStatus.ACTIVE), any());
        }

        @Test
        @DisplayName("Handles invalid status gracefully (no filter)")
        void getAllUsers_InvalidStatus_DefaultsToNoFilter() throws Exception {
            when(userRepository.findAdminUsers(any(), any(), isNull(), any(PageRequest.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/auth/admin/users")
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isOk());

            verify(userRepository).findAdminUsers(any(), any(), isNull(), any());
        }

        @Test
        @DisplayName("Handles blank search as null filter")
        void getAllUsers_BlankSearch_BecomesNull() throws Exception {
            when(userRepository.findAdminUsers(isNull(), any(), any(), any(PageRequest.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/auth/admin/users")
                            .param("search", "   "))
                    .andExpect(status().isOk());

            verify(userRepository).findAdminUsers(isNull(), any(), any(), any());
        }
    }

    // ===== GET /admin/users/{id} =====

    @Nested
    @DisplayName("GET /admin/users/{id} - get user by ID")
    class GetUserById {

        @Test
        @DisplayName("Returns user when found")
        void getUserById_Exists_ReturnsUser() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));

            mockMvc.perform(get("/api/v1/auth/admin/users/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.data.email").value("rahul@example.com"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("Returns 400 when user not found")
        void getUserById_NotFound_ReturnsError() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/admin/users/999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /admin/users/{id}/name =====

    @Nested
    @DisplayName("GET /admin/users/{id}/name - lightweight name lookup")
    class GetUserName {

        @Test
        @DisplayName("Returns name, email, role when user exists")
        void getUserName_Exists_ReturnsName() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));

            mockMvc.perform(get("/api/v1/auth/admin/users/42/name"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.exists").value(true))
                    .andExpect(jsonPath("$.name").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.email").value("rahul@example.com"))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.userId").value(42));
        }

        @Test
        @DisplayName("Returns exists=false with placeholder when user not found")
        void getUserName_NotFound_ReturnsPlaceholder() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/admin/users/999/name"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.exists").value(false))
                    .andExpect(jsonPath("$.name").value("User #999"));
        }

        @Test
        @DisplayName("Returns exists=false for deleted users")
        void getUserName_Deleted_ReturnsPlaceholder() throws Exception {
            User deletedUser = User.builder().id(50L).name("Deleted User").isDeleted(true).build();
            when(userRepository.findById(50L)).thenReturn(Optional.of(deletedUser));

            mockMvc.perform(get("/api/v1/auth/admin/users/50/name"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.exists").value(false));
        }
    }

    // ===== PUT /admin/users/{id} =====

    @Nested
    @DisplayName("PUT /admin/users/{id} - update user")
    class UpdateUser {

        @Test
        @DisplayName("Updates user fields and returns updated user")
        void updateUser_ValidRequest_UpdatesAndReturns() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));
            when(roleRepository.findByName("CIVIL_ENGINEER")).thenReturn(Optional.of(customerRole));
            when(userRepository.save(any())).thenReturn(sampleUser);

            Map<String, String> request = Map.of(
                    "name", "Rahul Updated",
                    "email", "rahul.new@example.com",
                    "role", "CIVIL_ENGINEER"
            );

            mockMvc.perform(put("/api/v1/auth/admin/users/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User updated successfully"));

            verify(userRepository).save(any());
        }

        @Test
        @DisplayName("Returns 400 when user not found")
        void updateUser_NotFound_ReturnsError() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(put("/api/v1/auth/admin/users/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "New Name"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when role is invalid")
        void updateUser_InvalidRole_ReturnsError() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));
            when(roleRepository.findByName("INVALID_ROLE")).thenReturn(Optional.empty());

            mockMvc.perform(put("/api/v1/auth/admin/users/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "INVALID_ROLE"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== PUT /admin/users/{id}/status =====

    @Nested
    @DisplayName("PUT /admin/users/{id}/status - update status")
    class UpdateUserStatus {

        @Test
        @DisplayName("Updates status to SUSPENDED with lock duration")
        void updateUserStatus_Suspend_SetsLock() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any())).thenReturn(sampleUser);

            mockMvc.perform(put("/api/v1/auth/admin/users/42/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED", "reason", "Policy violation"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User status updated to SUSPENDED"));
        }

        @Test
        @DisplayName("Returns 400 for invalid status value")
        void updateUserStatus_InvalidStatus_ReturnsError() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));

            mockMvc.perform(put("/api/v1/auth/admin/users/42/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "INVALID"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when status field is blank")
        void updateUserStatus_BlankStatus_ReturnsValidationError() throws Exception {
            mockMvc.perform(put("/api/v1/auth/admin/users/42/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== DELETE /admin/users/{id} =====

    @Nested
    @DisplayName("DELETE /admin/users/{id} - soft-delete user")
    class DeleteUser {

        @Test
        @DisplayName("Soft-deletes user and returns success")
        void deleteUser_Exists_MarksDeleted() throws Exception {
            when(userRepository.findById(42L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any())).thenReturn(sampleUser);

            mockMvc.perform(delete("/api/v1/auth/admin/users/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User deleted successfully"));

            verify(userRepository).save(argThat(u ->
                    u.getIsDeleted() && u.getStatus() == UserStatus.DELETED));
        }

        @Test
        @DisplayName("Returns 400 when user not found")
        void deleteUser_NotFound_ReturnsError() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(delete("/api/v1/auth/admin/users/999"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== GET /admin/stats =====

    @Nested
    @DisplayName("GET /admin/stats - user statistics")
    class GetUserStats {

        @Test
        @DisplayName("Returns counts by status")
        void getUserStats_ReturnsCounts() throws Exception {
            when(userRepository.count()).thenReturn(100L);
            when(userRepository.countByStatus(UserStatus.PENDING_VERIFICATION)).thenReturn(5L);
            when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(80L);
            when(userRepository.countByStatus(UserStatus.SUSPENDED)).thenReturn(10L);
            when(userRepository.countByStatus(UserStatus.BANNED)).thenReturn(5L);

            mockMvc.perform(get("/api/v1/auth/admin/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.totalUsers").value(100))
                    .andExpect(jsonPath("$.activeUsers").value(80))
                    .andExpect(jsonPath("$.pendingVerifications").value(5))
                    .andExpect(jsonPath("$.suspendedUsers").value(10))
                    .andExpect(jsonPath("$.bannedUsers").value(5));
        }
    }
}
