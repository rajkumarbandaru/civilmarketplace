package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.payment.client.UserServiceClient;
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
@DisplayName("UserNameResolver (payment-service) - caching Feign client")
class UserNameResolverTest {

    @Mock
    private UserServiceClient userServiceClient;

    private UserNameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UserNameResolver(userServiceClient);
    }

    @Nested
    @DisplayName("resolve(userId)")
    class Resolve {

        @Test
        @DisplayName("Returns resolved name from Feign client")
        void resolve_FeignSuccess_ReturnsName() {
            Map<String, Object> response = Map.of(
                    "success", true, "exists", true,
                    "name", "Amit Singh", "email", "amit@example.com",
                    "role", "CONTRACTOR", "userId", 55
            );
            when(userServiceClient.getUserName(55L))
                    .thenReturn(ResponseEntity.ok(response));

            var result = resolver.resolve(55L);

            assertThat(result.name()).isEqualTo("Amit Singh");
            assertThat(result.resolved()).isTrue();
        }

        @Test
        @DisplayName("Returns placeholder when Feign fails")
        void resolve_FeignFailure_ReturnsFallback() {
            when(userServiceClient.getUserName(anyLong()))
                    .thenThrow(new RuntimeException("Timeout"));

            var result = resolver.resolve(1L);

            assertThat(result.name()).isEqualTo("User #1");
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("Returns null for null userId")
        void resolve_NullUserId_ReturnsNulls() {
            var result = resolver.resolve(null);
            assertThat(result.name()).isNull();
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("Returns placeholder when user not found in auth")
        void resolve_UserNotFound_ReturnsPlaceholder() {
            Map<String, Object> response = Map.of(
                    "success", false, "exists", false,
                    "name", "User #500", "userId", 500
            );
            when(userServiceClient.getUserName(500L))
                    .thenReturn(ResponseEntity.ok(response));

            var result = resolver.resolve(500L);

            assertThat(result.name()).isEqualTo("User #500");
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("Caches result after first call")
        void resolve_CachesResult() {
            Map<String, Object> response = Map.of(
                    "success", true, "exists", true,
                    "name", "Suresh Kumar", "email", "suresh@example.com",
                    "role", "WORKER", "userId", 33
            );
            when(userServiceClient.getUserName(33L))
                    .thenReturn(ResponseEntity.ok(response));

            resolver.resolve(33L);
            resolver.resolve(33L);
            resolver.resolve(33L);

            verify(userServiceClient, times(1)).getUserName(33L);
        }
    }
}
