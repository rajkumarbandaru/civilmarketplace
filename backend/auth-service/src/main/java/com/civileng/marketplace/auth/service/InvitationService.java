package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserInvitation;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserInvitationRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A new workspace's owner, and how they get in: the account is created with no password (the
 * operator who publishes the workspace never knows one), and a single-use link lets the owner set
 * their own. Architecture 03 §2, Step 2.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvitationService {

    static final String OWNER_ROLE = "SUPER_ADMIN";
    static final Duration VALIDITY = Duration.ofHours(72);
    static final int MIN_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'");

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserInvitationRepository invitations;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AccountIdentifiers identifiers;
    private final Clock clock;

    public record Owner(Long userId, String email, boolean created) { }

    public record InvitationPreview(String email, String name, LocalDateTime expiresAt) { }

    /**
     * The workspace's owner account, created without a password. Idempotent: a retried
     * provisioning step finds the account it made last time.
     */
    @Transactional
    public Owner ensureOwner(String name, String email) {
        String normalised = identifiers.normaliseEmail(email);
        var existing = users.findByEmailAndIsDeletedFalse(normalised);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!OWNER_ROLE.equals(user.getRole().getName())) {
                throw new IllegalArgumentException("An account with that email already exists here with another role");
            }
            return new Owner(user.getId(), user.getEmail(), false);
        }
        Role role = roles.findByName(OWNER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role " + OWNER_ROLE + " is missing in this workspace"));
        User user = new User();
        user.setName(name == null || name.isBlank() ? normalised : name.trim());
        user.setEmail(normalised);
        user.setRole(role);
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user = users.save(user);
        log.info("Created workspace owner account {} (no password until the invitation is used)", user.getId());
        return new Owner(user.getId(), user.getEmail(), true);
    }

    /**
     * Issues a fresh link (any earlier unused one stops working) and hands it to notification-service
     * to email. The raw token exists only in that message; the database keeps its hash.
     */
    @Transactional
    public LocalDateTime invite(Long userId, String linkBase, String workspaceName) {
        User user = users.findById(userId).orElseThrow(() -> new NoSuchElementException("No such user"));
        if (user.getPasswordHash() != null) {
            throw new IllegalArgumentException("This account already has a password");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        invitations.findByUserIdAndUsedAtIsNull(userId).forEach(i -> {
            i.setUsedAt(now);
            invitations.save(i);
        });

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime expires = now.plus(VALIDITY);
        invitations.save(UserInvitation.builder().userId(userId).tokenHash(hash(token)).expiresAt(expires).build());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", userId);
        event.put("email", user.getEmail());
        event.put("name", user.getName());
        event.put("workspaceName", workspaceName);
        event.put("link", stripSlash(linkBase) + "/invite/" + token);
        event.put("expiresAt", WHEN.format(expires.atOffset(ZoneOffset.UTC)));
        kafkaTemplate.send("user.invited", event);
        log.info("Invitation issued to user {} (expires {})", userId, expires);
        return expires;
    }

    /** Who a link is for, so the page can greet them. Unknown, used and expired links all read as invalid. */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        UserInvitation invitation = usable(token);
        User user = users.findById(invitation.getUserId()).orElseThrow(this::invalid);
        return new InvitationPreview(user.getEmail(), user.getName(), invitation.getExpiresAt());
    }

    /** Sets the first password, and burns the link. Receiving the email proved the address. */
    @Transactional
    public void accept(String token, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Use at least " + MIN_PASSWORD_LENGTH + " characters for your password");
        }
        UserInvitation invitation = usable(token);
        User user = users.findById(invitation.getUserId()).orElseThrow(this::invalid);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        invitation.setUsedAt(LocalDateTime.now(clock));
        invitations.save(invitation);
        log.info("User {} accepted their invitation", user.getId());
    }

    private UserInvitation usable(String token) {
        if (token == null || token.length() < 20) throw invalid();
        UserInvitation invitation = invitations.findByTokenHash(hash(token)).orElseThrow(this::invalid);
        if (invitation.getUsedAt() != null || invitation.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw invalid();
        }
        return invitation;
    }

    private NoSuchElementException invalid() {
        return new NoSuchElementException("This invitation link is not valid any more. Ask for a new one.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
