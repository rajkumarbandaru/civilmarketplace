package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.dto.RegisterRequest;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.security.JwtTokenProvider;
import com.civileng.marketplace.tenant.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Self-registration and the platform console, which has no members to sign up. */
class AuthServiceRegisterTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RefreshTokenService store = mock(RefreshTokenService.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);

    @SuppressWarnings("unchecked")
    private final AuthService service = new AuthService(users, roles, mock(PasswordEncoder.class), jwt,
            mock(OtpService.class), store, mock(KafkaTemplate.class), mock(AccountIdentifiers.class),
            new SessionIssuer(jwt, store), mock(MfaService.class));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static RegisterRequest request(String role) {
        RegisterRequest r = new RegisterRequest();
        r.setName("Ravi");
        r.setEmail("ravi@example.com");
        r.setPassword("Password123!");
        r.setRole(role);
        return r;
    }

    @Test
    void nobodySignsUpOnTheRkConsole() {
        TenantContext.set("platform");
        assertThatThrownBy(() -> service.register(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available on the platform console");
        assertThatThrownBy(() -> service.register(request("CIVIL_ENGINEER")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roles, never()).findByName(anyString());
    }

    @Test
    void platformRolesCannotBeSelfSelectedAnywhere() {
        TenantContext.set("civengmarket");
        assertThatThrownBy(() -> service.register(request("PLATFORM_OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");
    }
}
