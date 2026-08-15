package com.civileng.marketplace.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtTokenProvider jwtTokenProvider;

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

        String accessToken = jwtTokenProvider.generateAccessToken(userId, email, role, name);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        String targetUrl = UriComponentsBuilder
                .fromUriString(primaryRedirectUri())
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("userId", userId)
                .queryParam("email", email)
                .queryParam("name", name)
                .queryParam("role", role)
                .queryParam("provider", provider)
                // Let the builder do the escaping — hand-encoding the values first
                // double-encodes them (a space arrives as %2520).
                .encode()
                .build()
                .toUriString();

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
