package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.UserProfile;
import com.civileng.marketplace.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProfileController.class)
@DisplayName("AdminProfileController - admin profile data endpoints")
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.fail-fast=false",
    "spring.config.import=",
    "eureka.client.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class AdminProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private UserProfileRepository userProfileRepository;

    private UserProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = UserProfile.builder()
                .id(1L)
                .userId(42L)
                .city("Mumbai")
                .state("Maharashtra")
                .bio("Experienced civil engineer with 10 years in structural design")
                .languages("English, Hindi")
                .experienceYears(10)
                .isVerified(true)
                .isAvailable(true)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 0))
                .build();
    }

    @Nested
    @DisplayName("GET /admin/profiles")
    class GetAllProfiles {

        @Test
        @DisplayName("Returns paginated profiles")
        void getAllProfiles_ReturnsPage() throws Exception {
            Page<UserProfile> page = new PageImpl<>(List.of(sampleProfile));
            when(userProfileRepository.findAll(any(PageRequest.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/users/admin/profiles")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].userId").value(42))
                    .andExpect(jsonPath("$.data[0].city").value("Mumbai"))
                    .andExpect(jsonPath("$.data[0].bio").value("Experienced civil engineer with 10 years in structural design"))
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        @DisplayName("Returns empty array when no profiles")
        void getAllProfiles_NoProfiles_ReturnsEmpty() throws Exception {
            when(userProfileRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/users/admin/profiles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /admin/profiles/{userId}")
    class GetProfileByUserId {

        @Test
        @DisplayName("Returns profile when user exists")
        void getProfileByUserId_Exists_ReturnsProfile() throws Exception {
            when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(sampleProfile));

            mockMvc.perform(get("/api/v1/users/admin/profiles/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.isVerified").value(true))
                    .andExpect(jsonPath("$.data.experienceYears").value(10));
        }

        @Test
        @DisplayName("Returns null data with message when no profile exists")
        void getProfileByUserId_NotFound_ReturnsNullData() throws Exception {
            when(userProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/users/admin/profiles/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("No profile found for user 999"));
        }
    }

    @Nested
    @DisplayName("GET /admin/stats")
    class GetUserProfileStats {

        @Test
        @DisplayName("Returns profile statistics counts")
        void getUserProfileStats_ReturnsCounts() throws Exception {
            when(userProfileRepository.count()).thenReturn(500L);
            when(userProfileRepository.countByIsVerifiedTrue()).thenReturn(300L);
            when(userProfileRepository.countByIsAvailableTrue()).thenReturn(200L);

            mockMvc.perform(get("/api/v1/users/admin/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.totalProfiles").value(500))
                    .andExpect(jsonPath("$.verifiedProfiles").value(300))
                    .andExpect(jsonPath("$.availableWorkers").value(200));
        }
    }
}
