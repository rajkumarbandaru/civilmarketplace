package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserNameResolver - caching Feign client for user name resolution")
class UserNameResolverTest {

    @Mock
    private UserServiceClient userServiceClient;

    private UserNameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UserNameResolver(userServiceClient);
    }

    @Nested
    @DisplayName("resolve(userId) - happy path")
    class ResolveSuccess {

        @Test
        @DisplayName("Returns resolved name from Feign client")
        void resolve_FeignSuccess_ReturnsName() {
            Map<String, Object> response = Map.of(
                    "success", true, "exists", true,
                    "name", "Rahul Sharma", "email", "rahul@example.com",
                    "role", "CUSTOMER", "userId", 42
            );
            when(userServiceClient.getUserName(42L))
                    .thenReturn(ResponseEntity.ok(response));

            UserNameResolver.ResolvedUser result = resolver.resolve(42L);

            assertThat(result.name()).isEqualTo("Rahul Sharma");
            assertThat(result.email()).isEqualTo("rahul@example.com");
            assertThat(result.role()).isEqualTo("CUSTOMER");
            assertThat(result.resolved()).isTrue();
        }

        @Test
        @DisplayName("Caches the result and returns cached on subsequent call")
        void resolve_SecondCall_UsesCache() {
            Map<String, Object> response = Map.of(
                    "success", true, "exists", true,
                    "name", "Priya Patel", "email", "priya@example.com",
                    "role", "CIVIL_ENGINEER", "userId", 99
            );
            when(userServiceClient.getUserName(99L))
                    .thenReturn(ResponseEntity.ok(response));

            // First call - should hit Feign
            UserNameResolver.ResolvedUser first = resolver.resolve(99L);
            assertThat(first.name()).isEqualTo("Priya Patel");

            // Second call - should use cache, no Feign call
            UserNameResolver.ResolvedUser second = resolver.resolve(99L);
            assertThat(second.name()).isEqualTo("Priya Patel");

            verify(userServiceClient, times(1)).getUserName(99L);
        }
    }

    @Nested
    @DisplayName("resolve(userId) - edge cases")
    class ResolveEdgeCases {

        @Test
        @DisplayName("Returns null fields for null userId")
        void resolve_NullUserId_ReturnsNulls() {
            UserNameResolver.ResolvedUser result = resolver.resolve(null);

            assertThat(result.name()).isNull();
            assertThat(result.email()).isNull();
            assertThat(result.role()).isNull();
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("Returns placeholder when user doesn't exist in auth-service")
        void resolve_UserNotFound_ReturnsPlaceholder() {
            Map<String, Object> response = Map.of(
                    "success", false, "exists", false,
                    "name", "User #777", "userId", 777
            );
            when(userServiceClient.getUserName(777L))
                    .thenReturn(ResponseEntity.ok(response));

            UserNameResolver.ResolvedUser result = resolver.resolve(777L);

            assertThat(result.name()).isEqualTo("User #777");
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("Returns placeholder when Feign call throws exception")
        void resolve_FeignFailure_ReturnsFallback() {
            when(userServiceClient.getUserName(anyLong()))
                    .thenThrow(new RuntimeException("Connection refused"));

            UserNameResolver.ResolvedUser result = resolver.resolve(1L);

            assertThat(result.name()).isEqualTo("User #1");
            assertThat(result.resolved()).isFalse();
        }
    }
}
