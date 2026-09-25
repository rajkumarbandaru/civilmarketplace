package com.civileng.marketplace.admin.service;

import com.civileng.marketplace.admin.client.AuthServiceClient;
import com.civileng.marketplace.admin.client.UserServiceClient;
import com.civileng.marketplace.web.common.AccessDeniedException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserServiceInviteTest {

    private final AuthServiceClient auth = mock(AuthServiceClient.class);
    private final AdminUserService service = new AdminUserService(auth, mock(UserServiceClient.class));
    private final Map<String, String> request = Map.of("name", "Ravi", "email", "ravi@acme.in", "role", "SITE_ENGINEER",
            "linkBase", "http://acme.localhost:3000", "workspaceName", "Acme");

    @Test
    void anAdminsInvitationIsForwardedWithTheirRole() {
        when(auth.inviteMember("ADMIN", request)).thenReturn(ResponseEntity.ok(new HashMap<>(Map.of("success", true))));

        assertThat(service.inviteUser("ADMIN", request)).containsEntry("success", true);
        verify(auth).inviteMember("ADMIN", request);
    }

    @Test
    void nonAdminsCannotAddUsers() {
        assertThatThrownBy(() -> service.inviteUser("CUSTOMER", request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.inviteUser(null, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void authServicesMessageReachesTheForm() {
        Request req = Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        when(auth.inviteMember(eq("ADMIN"), any())).thenThrow(new FeignException.BadRequest("bad", req,
                "{\"message\":\"Someone with that email already has an account here\"}".getBytes(StandardCharsets.UTF_8), Map.of()));

        assertThatThrownBy(() -> service.inviteUser("ADMIN", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Someone with that email already has an account here");
    }

    @Test
    void resendingSendsOnlyTheLinkDetails() {
        when(auth.resendInvitation(eq(7L), any())).thenReturn(ResponseEntity.ok(Map.of("expiresAt", "2026-09-28T10:00")));

        assertThat(service.resendInvitation("SUPER_ADMIN", 7L, request)).containsEntry("success", true);
        verify(auth).resendInvitation(7L, Map.of("linkBase", "http://acme.localhost:3000", "workspaceName", "Acme"));
    }
}
