package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountIdentifiers identifiers;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getName();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OAuth2 provider");
        }

        // Normalised so a Google login for `Ravi@x.com` resolves to the existing
        // `ravi@x.com` account instead of trying to create a second one.
        String normalisedEmail = identifiers.normaliseEmail(email);
        User user = userRepository.findByEmailAndIsDeletedFalse(normalisedEmail)
                .orElseGet(() -> registerNewOAuth2User(normalisedEmail, name, provider, providerId));

        if (!user.getProvider().equals(provider)) {
            throw new OAuth2AuthenticationException(
                    "Email already registered with " + user.getProvider());
        }

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("provider", provider);
        attributes.put("providerId", providerId);
        attributes.put("userId", user.getId().toString());
        attributes.put("role", user.getRole().getName());

        return new DefaultOAuth2User(
                Collections.singleton(() -> "ROLE_" + user.getRole().getName()),
                attributes,
                "email");
    }

    private User registerNewOAuth2User(String email, String name,
                                        String provider, String providerId) {
        Role defaultRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new EntityNotFoundException(
                        "Default role CUSTOMER not found. Ensure seed data has been run."));

        User user = User.builder()
                .email(email)
                .name(name)
                .provider(provider)
                .providerId(providerId)
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .role(defaultRole)
                .build();

        return userRepository.save(user);
    }
}
