package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;
import com.civileng.marketplace.admin.uiconfig.service.UiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigServiceTest {

    // In-memory tables behind mocked repositories: enough SQL semantics for the service's queries.
    private final List<ConfigRelease> releaseRows = new ArrayList<>();
    private final List<ConfigVersion> versionRows = new ArrayList<>();
    private final Map<ConfigPointer.Key, ConfigPointer> pointerRows = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private ConfigService service;

    @BeforeEach
    void setUp() {
        ConfigReleaseRepository releases = mock(ConfigReleaseRepository.class);
        ConfigVersionRepository versions = mock(ConfigVersionRepository.class);
        ConfigPointerRepository pointers = mock(ConfigPointerRepository.class);

        when(releases.save(any())).thenAnswer(inv -> {
            ConfigRelease r = inv.getArgument(0);
            r.setId(ids.incrementAndGet());
            r.setCreatedAt(LocalDateTime.now());
            releaseRows.add(r);
            return r;
        });
        when(releases.findById(anyLong())).thenAnswer(inv -> releaseRows.stream()
                .filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());
        when(releases.findByScopeOrderByIdDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> releaseRows.stream()
                .filter(r -> r.getScope().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(ConfigRelease::getId).reversed())
                .limit(inv.<Pageable>getArgument(1).getPageSize()).toList());
        when(releases.existsByScopeAndSourceIn(anyString(), anyCollection())).thenAnswer(inv -> releaseRows.stream()
                .anyMatch(r -> r.getScope().equals(inv.getArgument(0)) && inv.<Collection<?>>getArgument(1).contains(r.getSource())));
        when(releases.countByScope(anyString())).thenAnswer(inv ->
                releaseRows.stream().filter(r -> r.getScope().equals(inv.getArgument(0))).count());

        when(versions.save(any())).thenAnswer(inv -> {
            ConfigVersion v = inv.getArgument(0);
            v.setId(ids.incrementAndGet());
            versionRows.add(v);
            return v;
        });
        when(versions.findById(anyLong())).thenAnswer(inv -> versionRows.stream()
                .filter(v -> v.getId().equals(inv.getArgument(0))).findFirst());
        when(versions.findByReleaseId(anyLong())).thenAnswer(inv -> versionRows.stream()
                .filter(v -> v.getReleaseId().equals(inv.getArgument(0))).toList());
        when(versions.findFirstByScopeAndDocumentAndReleaseIdLessThanEqualOrderByIdDesc(anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> versionRows.stream()
                        .filter(v -> v.getScope().equals(inv.getArgument(0)) && v.getDocument().equals(inv.getArgument(1))
                                && v.getReleaseId() <= inv.<Long>getArgument(2))
                        .max(Comparator.comparing(ConfigVersion::getId)));
        when(versions.maxVersionNo(anyString(), anyString())).thenAnswer(inv -> versionRows.stream()
                .filter(v -> v.getScope().equals(inv.getArgument(0)) && v.getDocument().equals(inv.getArgument(1)))
                .mapToInt(ConfigVersion::getVersionNo).max().orElse(0));

        when(pointers.save(any())).thenAnswer(inv -> {
            ConfigPointer p = inv.getArgument(0);
            pointerRows.put(p.getId(), p);
            return p;
        });
        when(pointers.findByIdScope(anyString())).thenAnswer(inv -> pointerRows.values().stream()
                .filter(p -> p.getId().getScope().equals(inv.getArgument(0))).toList());
        when(pointers.findAll()).thenAnswer(inv -> new ArrayList<>(pointerRows.values()));

        service = new ConfigService(releases, versions, pointers, new ObjectMapper());
    }

    private static Map<ConfigDocument, Map<String, Object>> theme(String primary) {
        return Map.of(ConfigDocument.THEME, Map.of("mode", "light", "primaryColor", primary));
    }

    private ConfigService.ReleaseView publish(String primary) {
        return service.publish(ConfigScope.TENANT, theme(primary), ConfigRelease.Source.CONSOLE, 1L, "edit", null).orElseThrow();
    }

    @Test
    void publishMakesANewVersionLiveAndKeepsTheOldOne() {
        publish("#111111");
        publish("#222222");
        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.THEME)).containsEntry("primaryColor", "#222222");
        assertThat(versionRows).extracting(ConfigVersion::getVersionNo).containsExactly(1, 2);
        assertThat(versionRows.get(0).getContent()).contains("#111111");
    }

    @Test
    void onlyChangedDocumentsGetVersionsAndAnUnchangedSaveIsNoRelease() {
        ThemeUpdateCommand form = new ThemeUpdateCommand("dark", "#1a73e8", null, null, null, 12, null,
                "Acme", null, "flat", null, null, null, null);
        service.publish(ConfigScope.TENANT, UiConfigService.documentsOf(form), ConfigRelease.Source.CONSOLE, 1L, "a", null);
        // layout was left blank: empty, the same as nothing live, so it gets no version.
        assertThat(versionRows).extracting(ConfigVersion::getDocument).containsExactlyInAnyOrder("branding", "theme", "style");

        ThemeUpdateCommand recolour = new ThemeUpdateCommand("dark", "#0b8043", null, null, null, 12, null,
                "Acme", null, "flat", null, null, null, null);
        ConfigService.ReleaseView r = service.publish(ConfigScope.TENANT, UiConfigService.documentsOf(recolour),
                ConfigRelease.Source.CONSOLE, 1L, "b", null).orElseThrow();
        assertThat(r.documents()).containsExactly("theme");

        assertThat(service.publish(ConfigScope.TENANT, UiConfigService.documentsOf(recolour),
                ConfigRelease.Source.CONSOLE, 1L, "c", null)).isEmpty();
        assertThat(releaseRows).hasSize(2);
    }

    @Test
    void anInvalidPublishChangesNothing() {
        publish("#111111");
        assertThatThrownBy(() -> service.publish(ConfigScope.TENANT,
                Map.of(ConfigDocument.THEME, Map.of("primaryColor", "red"), ConfigDocument.STYLE, Map.of("density", "cozy")),
                ConfigRelease.Source.CONSOLE, 1L, "bad", null))
                .isInstanceOf(ConfigService.ConfigValidationException.class)
                .hasMessageContaining("theme.primaryColor").hasMessageContaining("style.density");
        assertThat(releaseRows).hasSize(1);
        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.THEME)).containsEntry("primaryColor", "#111111");
    }

    @Test
    void historyIsNewestFirstAndMarksWhatIsLive() {
        publish("#111111");
        publish("#222222");
        List<ConfigService.ReleaseView> h = service.history(ConfigScope.TENANT, 10);
        assertThat(h).extracting(ConfigService.ReleaseView::live).containsExactly(true, false);
        assertThat(h.get(0).id()).isGreaterThan(h.get(1).id());
    }

    @Test
    void diffShowsEachChangedKeyAgainstWhatWasLiveBefore() {
        publish("#111111");
        ConfigService.ReleaseView second = publish("#222222");
        assertThat(service.diff(second.id()).changes())
                .containsExactly(new ConfigService.KeyChange("theme", "primaryColor", "#111111", "#222222"));
    }

    @Test
    void rollbackRepublishesOldContentAsANewReleaseAndRecordsWhy() {
        ConfigService.ReleaseView first = publish("#111111");
        publish("#222222");

        ConfigService.ReleaseView back = service.rollback(first.id(), "client disliked it", 9L);

        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.THEME)).containsEntry("primaryColor", "#111111");
        assertThat(back.source()).isEqualTo("ROLLBACK");
        assertThat(back.rollbackOfReleaseId()).isEqualTo(first.id());
        assertThat(back.changeNote()).isEqualTo("Rolled back to release #" + first.id() + ": client disliked it");
        assertThat(releaseRows).hasSize(3);
        assertThat(versionRows).extracting(ConfigVersion::getVersionNo).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> service.rollback(first.id(), null, 9L)).hasMessageContaining("already what is live");
    }

    @Test
    void rollbackRestoresDocumentsThatDidNotExistYetAsEmpty() {
        ConfigService.ReleaseView first = publish("#111111");
        service.publish(ConfigScope.TENANT, Map.of(ConfigDocument.BRANDING, Map.of("brandName", "Later")),
                ConfigRelease.Source.CONSOLE, 1L, "brand", null);
        service.rollback(first.id(), null, 1L);
        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.BRANDING)).isEmpty();
    }

    @Test
    void rollbackIsRevalidatedAgainstTodaysSchema() {
        // Content stored when the rules were looser (the import copies old rows unchecked).
        ConfigRelease old = ConfigRelease.builder().scope("TENANT").source(ConfigRelease.Source.SEED).build();
        old.setId(ids.incrementAndGet());
        releaseRows.add(old);
        versionRows.add(ConfigVersion.builder().id(ids.incrementAndGet()).releaseId(old.getId()).scope("TENANT")
                .document("theme").versionNo(1).content("{\"primaryColor\":\"blue\"}").contentHash("x").build());
        publish("#222222");
        assertThatThrownBy(() -> service.rollback(old.getId(), null, 1L))
                .isInstanceOf(ConfigService.ConfigValidationException.class);
        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.THEME)).containsEntry("primaryColor", "#222222");
    }

    @Test
    void scopesAreIndependentAndTheWorkspaceKnowsWhichRolesOverride() {
        publish("#111111");
        service.publish(ConfigScope.role("ARCHITECT"), theme("#333333"), ConfigRelease.Source.CONSOLE, 1L, "r", null);
        assertThat(service.live(ConfigScope.TENANT).get(ConfigDocument.THEME)).containsEntry("primaryColor", "#111111");
        assertThat(service.rolesWithOverrides()).containsExactly("ARCHITECT");
        service.publish(ConfigScope.role("ARCHITECT"), Map.of(ConfigDocument.THEME, Map.of()), ConfigRelease.Source.CONSOLE, 1L, "reset", null);
        assertThat(service.rolesWithOverrides()).isEmpty();
    }

    @Test
    void customisedMeansTheWorkspacesOwnChangeNotOnboarding() {
        service.publish(ConfigScope.TENANT, theme("#111111"), ConfigRelease.Source.ONBOARDING, null, "onboard", null);
        assertThat(service.customisedByWorkspace(ConfigScope.TENANT)).isFalse();
        assertThat(service.hasNonSeedRelease(ConfigScope.TENANT)).isTrue();
        publish("#222222");
        assertThat(service.customisedByWorkspace(ConfigScope.TENANT)).isTrue();
    }

    @Test
    void levelPolicyIsEnforcedPerKey() {
        assertThat(ConfigScope.parse("ROLE:ARCHITECT")).isEqualTo(ConfigScope.role("ARCHITECT"));
        assertThat(ConfigScope.fromThemeScopeKey("PLATFORM")).isEqualTo(ConfigScope.TENANT);
        assertThat(ConfigDocument.THEME.keys().get("primaryColor").overridableAt())
                .contains(ConfigDocument.Level.TENANT, ConfigDocument.Level.ROLE);
    }
}
