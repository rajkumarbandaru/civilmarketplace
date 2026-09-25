package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserInvitation;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserInvitationRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvitationServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserInvitationRepository invitations = mock(UserInvitationRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final AccountIdentifiers identifiers = mock(AccountIdentifiers.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final List<UserInvitation> rows = new ArrayList<>();
    private Instant now = Instant.parse("2026-09-24T10:00:00Z");
    private InvitationService service;
    private final User owner = new User();

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        service = new InvitationService(users, roles, invitations, encoder, kafka, identifiers, clock);
        when(identifiers.normaliseEmail(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).trim().toLowerCase());
        Role superAdmin = new Role();
        superAdmin.setName("SUPER_ADMIN");
        when(roles.findByName("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        owner.setId(42L);
        owner.setEmail("asha@acme.in");
        owner.setName("Asha");
        owner.setRole(superAdmin);
        owner.setStatus(UserStatus.PENDING_VERIFICATION);
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(42L);
            return u;
        });
        when(users.findById(42L)).thenReturn(Optional.of(owner));
        when(invitations.save(any())).thenAnswer(inv -> {
            UserInvitation i = inv.getArgument(0);
            if (!rows.contains(i)) rows.add(i);
            return i;
        });
        when(invitations.findByTokenHash(anyString())).thenAnswer(inv -> rows.stream()
                .filter(i -> i.getTokenHash().equals(inv.getArgument(0))).findFirst());
        when(invitations.findByUserIdAndUsedAtIsNull(42L)).thenAnswer(inv -> rows.stream().filter(i -> i.getUsedAt() == null).toList());
    }

    @SuppressWarnings("unchecked")
    private String sendInvite() {
        service.invite(42L, "http://acme.localhost:3000/", "Acme Builders");
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka, atLeastOnce()).send(eq("user.invited"), event.capture());
        String link = (String) ((Map<String, Object>) event.getValue()).get("link");
        assertThat(link).startsWith("http://acme.localhost:3000/invite/");
        return link.substring(link.lastIndexOf('/') + 1);
    }

    @Test
    void createsTheOwnerWithoutAPasswordAndIsIdempotent() {
        when(users.findByEmailAndIsDeletedFalse("asha@acme.in")).thenReturn(Optional.empty());
        InvitationService.Owner created = service.ensureOwner("Asha", " Asha@Acme.in ");
        assertThat(created.created()).isTrue();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getRole().getName()).isEqualTo("SUPER_ADMIN");

        when(users.findByEmailAndIsDeletedFalse("asha@acme.in")).thenReturn(Optional.of(owner));
        assertThat(service.ensureOwner("Asha", "asha@acme.in").created()).isFalse();
    }

    @Test
    void storesOnlyTheHashAndTheLinkWorksOnce() {
        String token = sendInvite();
        assertThat(rows).singleElement().satisfies(i -> {
            assertThat(i.getTokenHash()).hasSize(64).isNotEqualTo(token);
            assertThat(i.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 10, 0));
        });
        assertThat(service.preview(token).email()).isEqualTo("asha@acme.in");

        service.accept(token, "correct horse battery");
        assertThat(encoder.matches("correct horse battery", owner.getPasswordHash())).isTrue();
        assertThat(owner.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(owner.getEmailVerified()).isTrue();
        assertThatThrownBy(() -> service.accept(token, "another long password")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aNewLinkRetiresTheOldOne() {
        String first = sendInvite();
        String second = sendInvite();
        assertThatThrownBy(() -> service.preview(first)).isInstanceOf(NoSuchElementException.class);
        assertThat(service.preview(second).name()).isEqualTo("Asha");
    }

    @Test
    void expiredOrMadeUpLinksAndShortPasswordsAreRefused() {
        String token = sendInvite();
        assertThatThrownBy(() -> service.accept(token, "short")).hasMessageContaining("at least 12");
        assertThatThrownBy(() -> service.preview("x".repeat(43))).isInstanceOf(NoSuchElementException.class);
        now = now.plus(Duration.ofHours(73));
        assertThatThrownBy(() -> service.preview(token)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void anAccountThatAlreadyHasAPasswordIsNotInvited() {
        owner.setPasswordHash("x");
        assertThatThrownBy(() -> service.invite(42L, "http://x", "X")).hasMessageContaining("already has a password");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAdminAddsAMemberWithARoleAndTheyGetALink() {
        Role engineer = new Role();
        engineer.setName("SITE_ENGINEER");
        when(roles.findByName("SITE_ENGINEER")).thenReturn(Optional.of(engineer));
        when(users.findByEmailAndIsDeletedFalse("ravi@acme.in")).thenReturn(Optional.empty());
        User[] created = new User[1];
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(77L);
            created[0] = u;
            return u;
        }).when(users).save(any());
        when(users.findById(77L)).thenAnswer(inv -> Optional.of(created[0]));

        InvitationService.Member member = service.inviteMember("Ravi", " Ravi@Acme.in", "site_engineer", "ADMIN",
                "http://acme.localhost:3000", "Acme Builders");

        assertThat(member.role()).isEqualTo("SITE_ENGINEER");
        assertThat(member.email()).isEqualTo("ravi@acme.in");
        assertThat(created[0].getPasswordHash()).isNull();
        assertThat(created[0].getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("user.invited"), event.capture());
        assertThat(((Map<String, Object>) event.getValue()).get("link").toString())
                .startsWith("http://acme.localhost:3000/invite/");
    }

    @Test
    void onlyASuperAdminCanAddASuperAdmin() {
        assertThatThrownBy(() -> service.inviteMember("X", "x@acme.in", "SUPER_ADMIN", "ADMIN",
                "http://acme.localhost:3000", "Acme"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void membersNeedAKnownRoleAFreeEmailAndAProperLink() {
        when(roles.findByName("PILOT")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inviteMember("X", "x@acme.in", "PILOT", "ADMIN",
                "http://acme.localhost:3000", "Acme")).hasMessageContaining("No role");

        when(users.findByEmailAndIsDeletedFalse("asha@acme.in")).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.inviteMember("Asha", "asha@acme.in", "SUPER_ADMIN", "SUPER_ADMIN",
                "http://acme.localhost:3000", "Acme")).hasMessageContaining("already has an account");

        assertThatThrownBy(() -> service.inviteMember("X", "x@acme.in", "SUPER_ADMIN", "SUPER_ADMIN",
                "javascript:alert(1)", "Acme")).hasMessageContaining("linkBase");
    }
}
