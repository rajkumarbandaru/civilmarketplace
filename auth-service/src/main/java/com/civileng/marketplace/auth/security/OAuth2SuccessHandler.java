package com.civileng.marketplace.auth.security;

import com.civileng.marketplace.auth.entity.User;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String provider = (String) attributes.get("provider");

        String accessToken = jwtTokenProvider.generateAccessToken(
                (String) attributes.get("sub"), email, "CUSTOMER", name);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                (String) attributes.get("sub"));

        String targetUrl = UriComponentsBuilder
                .fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("email", URLEncoder.encode(email, StandardCharsets.UTF_8))
                .queryParam("name", URLEncoder.encode(name, StandardCharsets.UTF_8))
                .queryParam("provider", provider)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
