package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.dto.CreateTenantRequest;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantDraft;
import com.civileng.marketplace.tenant.repository.TenantDraftRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.service.TenantService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * The Create Platform wizard's drafts. A draft holds what the operator has typed so far — the
 * shape of a create request plus the owner — and is not a tenant: nothing is reserved or built
 * until {@link #createTenant} turns it into a DRAFT tenant.
 */
@Service
@RequiredArgsConstructor
public class DraftService {

    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9]{1,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantDraftRepository drafts;
    private final TenantRepository tenants;
    private final TenantService tenantService;
    private final Validator validator;
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** A check result the wizard shows beside the section it belongs to. */
    public record Issue(String section, String message) { }

    public record DraftView(Long id, String title, JsonNode data, int version, String status, String tenantKey,
                            String updatedBy, java.time.LocalDateTime updatedAt, List<Issue> issues) { }

    @Transactional(readOnly = true)
    public List<DraftView> open() {
        return drafts.findByStatusOrderByUpdatedAtDesc(TenantDraft.Status.OPEN).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public DraftView get(Long id) {
        return view(find(id));
    }

    @Transactional
    public DraftView create(JsonNode data, String actor) {
        TenantDraft draft = drafts.save(TenantDraft.builder().data(write(data)).title(title(data))
                .status(TenantDraft.Status.OPEN).createdBy(actor).updatedBy(actor).build());
        return view(draft);
    }

    /**
     * Autosave. {@code version} is the one the editor loaded: if someone else saved since, this is
     * refused (409) rather than overwriting them.
     */
    @Transactional
    public DraftView save(Long id, int version, JsonNode data, String actor) {
        TenantDraft draft = openDraft(id);
        if (draft.getVersion() != version) {
            throw new ObjectOptimisticLockingFailureException(TenantDraft.class, id);
        }
        draft.setData(write(data));
        draft.setTitle(title(data));
        draft.setUpdatedBy(actor);
        return view(drafts.saveAndFlush(draft));
    }

    @Transactional
    public void discard(Long id) {
        TenantDraft draft = openDraft(id);
        draft.setStatus(TenantDraft.Status.DISCARDED);
        drafts.save(draft);
    }

    /** Step 14: the draft becomes a DRAFT tenant — key and subdomain reserved, nothing provisioned. */
    @Transactional
    public Tenant createTenant(Long id, String actor) {
        TenantDraft draft = openDraft(id);
        List<Issue> issues = issues(read(draft.getData()));
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", issues.stream().map(Issue::message).toList()));
        }
        CreateTenantRequest request;
        try {
            request = json.treeToValue(read(draft.getData()), CreateTenantRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("The draft could not be read as a tenant: " + e.getMessage());
        }
        List<String> violations = validator.validate(request).stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList();
        if (!violations.isEmpty()) throw new IllegalArgumentException(String.join("; ", violations));

        Tenant tenant = tenantService.create(request, actor);
        draft.setStatus(TenantDraft.Status.CREATED);
        draft.setTenantKey(tenant.getTenantKey());
        draft.setUpdatedBy(actor);
        drafts.save(draft);
        return tenant;
    }

    /**
     * Step checks, run on every save: what would stop "Create" from succeeding, by section. The
     * uniqueness checks are advisory until Create, which reserves atomically.
     */
    List<Issue> issues(JsonNode d) {
        List<Issue> out = new ArrayList<>();
        String name = text(d, "name"), key = text(d, "tenantKey"), contact = text(d, "contactEmail");
        String ownerEmail = text(d, "ownerEmail"), subdomain = text(d, "subdomain");
        if (name == null || name.length() < 2) out.add(new Issue("platform", "Enter a platform name (2–60 characters)"));
        else if (name.length() > 60) out.add(new Issue("platform", "The platform name can be at most 60 characters"));
        if (key == null || !KEY.matcher(key).matches()) {
            out.add(new Issue("platform", "The tenant key is 2–31 lowercase letters and digits, starting with a letter"));
        } else if (tenants.existsByTenantKey(key)) {
            out.add(new Issue("platform", "Tenant key '" + key + "' is taken"));
        }
        if (contact == null || !EMAIL.matcher(contact).matches()) out.add(new Issue("platform", "Enter a valid contact email"));
        if (ownerEmail == null || !EMAIL.matcher(ownerEmail).matches()) {
            out.add(new Issue("owner", "Enter the owner's email: they are invited to set their own password"));
        }
        String sub = subdomain != null ? subdomain : key;
        if (sub != null && KEY.matcher(sub).matches() && tenants.existsBySubdomain(sub)) {
            out.add(new Issue("domain", "Subdomain '" + sub + "' is taken"));
        }
        return out;
    }

    private DraftView view(TenantDraft d) {
        JsonNode data = read(d.getData());
        return new DraftView(d.getId(), d.getTitle(), data, d.getVersion(), d.getStatus().name(), d.getTenantKey(),
                d.getUpdatedBy(), d.getUpdatedAt(), d.getStatus() == TenantDraft.Status.OPEN ? issues(data) : List.of());
    }

    private TenantDraft find(Long id) {
        return drafts.findById(id).orElseThrow(() -> new NoSuchElementException("No draft #" + id));
    }

    private TenantDraft openDraft(Long id) {
        TenantDraft draft = find(id);
        if (draft.getStatus() != TenantDraft.Status.OPEN) throw new IllegalArgumentException("Draft #" + id + " is " + draft.getStatus());
        return draft;
    }

    private static String title(JsonNode d) {
        String name = text(d, "name");
        return name == null ? null : name.length() > 120 ? name.substring(0, 120) : name;
    }

    private static String text(JsonNode d, String field) {
        JsonNode n = d == null ? null : d.get(field);
        return n == null || n.isNull() || n.asText().isBlank() ? null : n.asText().trim();
    }

    private JsonNode read(String s) {
        try {
            return json.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(JsonNode n) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException("A draft is a JSON object");
        return n.toString();
    }

    @SuppressWarnings("unused")
    private static String describe(ConstraintViolation<?> v) {
        return v.getPropertyPath() + " " + v.getMessage();
    }
}
