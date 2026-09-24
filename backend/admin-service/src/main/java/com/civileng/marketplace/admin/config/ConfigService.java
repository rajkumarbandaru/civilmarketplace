package com.civileng.marketplace.admin.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Versioned configuration documents: publish, history, diff and rollback (architecture 04 §7–§9).
 *
 * <p>Every change is a release of new, immutable document versions; the publish pointer of each
 * document moves to its new version in the same transaction, so readers see either all of a
 * release or none of it. Rollback is roll-forward: the content that was live after an earlier
 * release is re-validated against today's schema and published as a new release, so history is
 * linear and says who went back, when and why.
 *
 * <p>Runs in the calling tenant's schema like the rest of admin-service; a workspace's history is
 * its own.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };

    private final ConfigReleaseRepository releases;
    private final ConfigVersionRepository versions;
    private final ConfigPointerRepository pointers;
    private final ObjectMapper json;

    public record ReleaseView(Long id, String scope, String source, String changeNote, Long rollbackOfReleaseId,
                              Long createdBy, LocalDateTime createdAt, List<String> documents, boolean live) { }

    public record KeyChange(String document, String key, Object before, Object after) { }

    public record ReleaseDiff(ReleaseView release, List<KeyChange> changes) { }

    /** A publish refused by validation; the message lists every error. */
    public static class ConfigValidationException extends IllegalArgumentException {
        private final Map<String, ConfigValidator.Report> reports;

        ConfigValidationException(Map<String, ConfigValidator.Report> reports) {
            super(String.join("; ", reports.values().stream().flatMap(r -> r.errors().stream()).toList()));
            this.reports = reports;
        }

        public Map<String, ConfigValidator.Report> reports() {
            return reports;
        }
    }

    /** What is live in a scope, per document. Absent keys (and stored nulls) inherit. */
    @Transactional(readOnly = true)
    public Map<ConfigDocument, Map<String, Object>> live(ConfigScope scope) {
        Map<ConfigDocument, Map<String, Object>> out = new EnumMap<>(ConfigDocument.class);
        for (ConfigPointer p : pointers.findByIdScope(scope.key())) {
            versions.findById(p.getVersionId()).ifPresent(v ->
                    out.put(ConfigDocument.fromKey(v.getDocument()), read(v.getContent())));
        }
        return out;
    }

    /** Roles whose workspace currently overrides at least one setting (one pass over the pointers). */
    @Transactional(readOnly = true)
    public Set<String> rolesWithOverrides() {
        Set<String> roles = new TreeSet<>();
        for (ConfigPointer p : pointers.findAll()) {
            String scope = p.getId().getScope();
            if (!scope.startsWith("ROLE:") || roles.contains(scope.substring(5))) continue;
            versions.findById(p.getVersionId())
                    .filter(v -> !normalise(read(v.getContent())).isEmpty())
                    .ifPresent(v -> roles.add(scope.substring(5)));
        }
        return roles;
    }

    /** Id of the newest release in a scope, 0 if none: changes whenever anything is published. */
    @Transactional(readOnly = true)
    public long liveReleaseId(ConfigScope scope) {
        return releases.findByScopeOrderByIdDesc(scope.key(), PageRequest.of(0, 1)).stream()
                .findFirst().map(ConfigRelease::getId).orElse(0L);
    }

    /**
     * Publishes the given documents' content as one release. Documents not named are left as they
     * are; one named with the same content as is live gets no new version. Returns empty when
     * nothing changed (no empty releases).
     *
     * @throws ConfigValidationException if any document fails validation — nothing is published
     */
    @Transactional
    public Optional<ReleaseView> publish(ConfigScope scope, Map<ConfigDocument, Map<String, Object>> contents,
                                         ConfigRelease.Source source, Long actor, String note, Long rollbackOf) {
        Map<ConfigDocument, Map<String, Object>> normalised = new EnumMap<>(ConfigDocument.class);
        contents.forEach((doc, content) -> normalised.put(doc, normalise(content)));

        Map<String, ConfigValidator.Report> reports = new LinkedHashMap<>();
        normalised.forEach((doc, content) -> reports.put(doc.key(), ConfigValidator.validate(doc, scope, content)));
        if (reports.values().stream().anyMatch(r -> !r.ok())) {
            throw new ConfigValidationException(reports);
        }

        Map<ConfigDocument, Map<String, Object>> current = live(scope);
        Map<ConfigDocument, Map<String, Object>> changed = new EnumMap<>(ConfigDocument.class);
        normalised.forEach((doc, content) -> {
            if (!content.equals(normalise(current.getOrDefault(doc, Map.of())))) changed.put(doc, content);
        });
        if (changed.isEmpty()) {
            return Optional.empty();
        }

        ConfigRelease release = releases.save(ConfigRelease.builder()
                .scope(scope.key()).source(source).createdBy(actor)
                .changeNote(truncate(note, 500)).rollbackOfReleaseId(rollbackOf).build());
        changed.forEach((doc, content) -> {
            String text = write(content);
            ConfigVersion version = versions.save(ConfigVersion.builder()
                    .releaseId(release.getId())
                    .scope(scope.key())
                    .document(doc.key())
                    .versionNo(versions.maxVersionNo(scope.key(), doc.key()) + 1)
                    .content(text)
                    .contentHash(sha256(text))
                    .validationReport(write(Map.of("warnings", reports.get(doc.key()).warnings())))
                    .build());
            pointers.save(new ConfigPointer(new ConfigPointer.Key(scope.key(), doc.key()), version.getId()));
        });
        log.info("Published release {} in {} ({}): {}", release.getId(), scope, source,
                changed.keySet().stream().map(ConfigDocument::key).toList());
        return Optional.of(view(releases.findById(release.getId()).orElseThrow(), true));
    }

    @Transactional(readOnly = true)
    public List<ReleaseView> history(ConfigScope scope, int limit) {
        List<ConfigRelease> list = releases.findByScopeOrderByIdDesc(scope.key(), PageRequest.of(0, Math.min(limit, 200)));
        List<ReleaseView> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(view(list.get(i), i == 0));
        return out;
    }

    /** What a release changed, key by key, against what was live just before it. */
    @Transactional(readOnly = true)
    public ReleaseDiff diff(Long releaseId) {
        ConfigRelease release = find(releaseId);
        List<KeyChange> changes = new ArrayList<>();
        for (ConfigVersion v : versions.findByReleaseId(releaseId)) {
            Map<String, Object> before = versions
                    .findFirstByScopeAndDocumentAndReleaseIdLessThanEqualOrderByIdDesc(v.getScope(), v.getDocument(), releaseId - 1)
                    .map(p -> normalise(read(p.getContent()))).orElse(Map.of());
            changes.addAll(changes(v.getDocument(), before, normalise(read(v.getContent()))));
        }
        return new ReleaseDiff(view(release, isLatest(release)), changes);
    }

    /**
     * Makes live again what was live right after {@code releaseId}: every document that has
     * changed since goes back, as one new release. Re-validated first — content that was fine then
     * may not be now.
     */
    @Transactional
    public ReleaseView rollback(Long releaseId, String reason, Long actor) {
        ConfigRelease target = find(releaseId);
        ConfigScope scope = ConfigScope.parse(target.getScope());
        Map<ConfigDocument, Map<String, Object>> state = new EnumMap<>(ConfigDocument.class);
        for (ConfigDocument doc : ConfigDocument.values()) {
            state.put(doc, versions
                    .findFirstByScopeAndDocumentAndReleaseIdLessThanEqualOrderByIdDesc(scope.key(), doc.key(), releaseId)
                    .map(v -> read(v.getContent())).orElse(Map.of()));
        }
        String note = "Rolled back to release #" + releaseId + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
        return publish(scope, state, ConfigRelease.Source.ROLLBACK, actor, note, releaseId)
                .orElseThrow(() -> new IllegalArgumentException("That version is already what is live"));
    }

    /** True once this workspace's own admin has saved (or rolled back) the given scope. */
    @Transactional(readOnly = true)
    public boolean customisedByWorkspace(ConfigScope scope) {
        return releases.existsByScopeAndSourceIn(scope.key(),
                List.of(ConfigRelease.Source.CONSOLE, ConfigRelease.Source.ROLLBACK));
    }

    /** True once anything beyond the shipped default has been published for the scope. */
    @Transactional(readOnly = true)
    public boolean hasNonSeedRelease(ConfigScope scope) {
        return releases.existsByScopeAndSourceIn(scope.key(), List.of(ConfigRelease.Source.ONBOARDING,
                ConfigRelease.Source.OPERATOR, ConfigRelease.Source.CONSOLE, ConfigRelease.Source.ROLLBACK));
    }

    @Transactional(readOnly = true)
    public long releaseCount(ConfigScope scope) {
        return releases.countByScope(scope.key());
    }

    // ------------------------------------------------------------------ helpers

    private ConfigRelease find(Long id) {
        return releases.findById(id).orElseThrow(() -> new NoSuchElementException("No release #" + id));
    }

    private boolean isLatest(ConfigRelease r) {
        return liveReleaseId(ConfigScope.parse(r.getScope())) == r.getId();
    }

    private ReleaseView view(ConfigRelease r, boolean live) {
        List<String> docs = versions.findByReleaseId(r.getId()).stream().map(ConfigVersion::getDocument).sorted().toList();
        return new ReleaseView(r.getId(), r.getScope(), r.getSource().name(), r.getChangeNote(),
                r.getRollbackOfReleaseId(), r.getCreatedBy(), r.getCreatedAt(), docs, live);
    }

    static List<KeyChange> changes(String document, Map<String, Object> before, Map<String, Object> after) {
        SortedSet<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<KeyChange> out = new ArrayList<>();
        for (String k : keys) {
            if (!Objects.equals(before.get(k), after.get(k))) out.add(new KeyChange(document, k, before.get(k), after.get(k)));
        }
        return out;
    }

    /** Sorted, with nulls and blank strings removed: absent means inherit, and one spelling of it. */
    static Map<String, Object> normalise(Map<String, Object> content) {
        Map<String, Object> out = new TreeMap<>();
        if (content != null) {
            content.forEach((k, v) -> {
                if (v == null || (v instanceof String s && s.isBlank())) return;
                out.put(k, v instanceof String s ? s.trim() : v);
            });
        }
        return out;
    }

    private Map<String, Object> read(String text) {
        try {
            return json.readValue(text, MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable configuration content", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
