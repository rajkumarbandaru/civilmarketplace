package com.civileng.marketplace.auth.security;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.repository.UserRepository;
import com.civileng.marketplace.auth.service.MfaService;
import com.civileng.marketplace.auth.service.SessionIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final SessionIssuer sessionIssuer;
    private final MfaService mfaService;

    @Value("${app.oauth2.authorized-redirect-uris}")
    private String[] authorizedRedirectUris;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = asString(attributes.get("email"));
        String name = asString(attributes.get("name"));
        String provider = asString(attributes.get("provider"));
        // CustomOAuth2UserService resolves these against our own user table. The raw
        // provider claims are provider-specific ("sub" is Google/Apple, Facebook uses
        // "id"), so never key off them here.
        String userId = asString(attributes.get("userId"));
        String role = asString(attributes.get("role"));

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalStateException("Social sign-in resolved to no user"));

        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(primaryRedirectUri());
        if (mfaService.required(user)) {
            // The provider proved who they are, not the second factor: hand over an MFA ticket,
            // never tokens, and let the frontend finish sign-in.
            AuthResponse challenge = mfaService.challenge(user);
            target.queryParam("mfaToken", challenge.getMfaToken())
                    .queryParam("mfaSetup", challenge.getMfaSetupRequired());
        } else {
            // Through the SessionIssuer so the refresh token is registered for rotation; a token
            // minted here directly was unknown to the rotation store and failed its first refresh.
            AuthResponse session = sessionIssuer.issue(user, "Login successful");
            target.queryParam("accessToken", session.getAccessToken())
                    .queryParam("refreshToken", session.getRefreshToken())
                    .queryParam("userId", userId)
                    .queryParam("email", email)
                    .queryParam("name", name)
                    .queryParam("role", role)
                    .queryParam("provider", provider);
        }
        // Let the builder do the escaping — hand-encoding the values first double-encodes them
        // (a space arrives as %2520).
        String targetUrl = target.encode().build().toUriString();

        log.info("OAuth2 login succeeded for {} via {}", email, provider);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * The frontend to hand the tokens to. This is deliberately taken from configuration
     * rather than from a request parameter: at this point we are handling the provider's
     * callback (code + state only), and honouring a caller-supplied target would let any
     * site that can start the flow receive the tokens.
     *
     * Set app.oauth2.authorized-redirect-uris (first entry wins) per environment.
     */
    private String primaryRedirectUri() {
        return authorizedRedirectUris[0].trim();
    }

    private String asString(Object value) {
        return value == null ? "" : Objects.toString(value);
    }
}
