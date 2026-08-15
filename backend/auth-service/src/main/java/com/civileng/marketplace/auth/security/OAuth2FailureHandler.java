package com.civileng.marketplace.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Sends a failed social login back to the frontend login page with a readable
 * message, instead of leaving the user on Spring's default error page.
 */
@Component
@Slf4j
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.authorized-redirect-uris}")
    private String[] authorizedRedirectUris;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException {

        log.warn("OAuth2 login failed: {}", exception.getMessage());

        // The configured entry points at /oauth2/redirect; the login page is its sibling.
        String loginUrl = authorizedRedirectUris[0].trim().replace("/oauth2/redirect", "/login");

        String targetUrl = UriComponentsBuilder
                .fromUriString(loginUrl)
                .queryParam("error", exception.getMessage())
                .encode()
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
