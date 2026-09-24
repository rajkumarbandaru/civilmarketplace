package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantDraft;
import com.civileng.marketplace.tenant.repository.TenantDraftRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.service.TenantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftServiceTest {

    private final TenantDraftRepository drafts = mock(TenantDraftRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private DraftService service;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new DraftService(drafts, tenants, tenantService, Validation.buildDefaultValidatorFactory().getValidator());
        when(drafts.save(any())).thenAnswer(inv -> {
            TenantDraft d = inv.getArgument(0);
            if (d.getId() == null) d.setId(5L);
            if (d.getVersion() == null) d.setVersion(0);
            return d;
        });
        when(drafts.saveAndFlush(any())).thenAnswer(inv -> {
            TenantDraft d = inv.getArgument(0);
            d.setVersion(d.getVersion() + 1);
            return d;
        });
    }

    private JsonNode data(String extra) throws Exception {
        return json.readTree("{\"name\":\"Acme Builders\",\"tenantKey\":\"acme\",\"contactEmail\":\"ops@acme.in\""
                + ",\"ownerName\":\"Asha\",\"ownerEmail\":\"asha@acme.in\"" + extra + "}");
    }

    private TenantDraft stored(JsonNode d) {
        TenantDraft draft = TenantDraft.builder().id(5L).data(d.toString()).version(3).status(TenantDraft.Status.OPEN).build();
        when(drafts.findById(5L)).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    void aCompleteDraftHasNoIssues() throws Exception {
        assertThat(service.issues(data(""))).isEmpty();
    }

    @Test
    void reportsIssuesBySection() throws Exception {
        when(tenants.existsBySubdomain("taken")).thenReturn(true);
        var issues = service.issues(json.readTree("{\"name\":\"A\",\"tenantKey\":\"9bad\",\"subdomain\":\"taken\"}"));
        assertThat(issues).extracting(DraftService.Issue::section).containsExactlyInAnyOrder("platform", "platform", "platform", "owner", "domain");
    }

    @Test
    void autosaveRefusesAStaleVersion() throws Exception {
        stored(data(""));
        assertThatThrownBy(() -> service.save(5L, 2, data(""), "1")).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(service.save(5L, 3, data(",\"plan\":\"GOLD\""), "1").version()).isEqualTo(4);
    }

    @Test
    void createTurnsTheDraftIntoADraftTenant() throws Exception {
        TenantDraft draft = stored(data(",\"vertical\":\"CIVIL_MARKETPLACE\""));
        when(tenantService.create(any(), eq("1"))).thenAnswer(inv -> Tenant.builder().tenantKey("acme").build());
        service.createTenant(5L, "1");
        verify(tenantService).create(argThat(r -> r.getOwnerEmail().equals("asha@acme.in") && r.getTenantKey().equals("acme")), eq("1"));
        assertThat(draft.getStatus()).isEqualTo(TenantDraft.Status.CREATED);
        assertThat(draft.getTenantKey()).isEqualTo("acme");
        assertThatThrownBy(() -> service.createTenant(5L, "1")).hasMessageContaining("is CREATED");
    }

    @Test
    void createRefusesAnIncompleteDraft() throws Exception {
        stored(json.readTree("{\"name\":\"Acme\"}"));
        assertThatThrownBy(() -> service.createTenant(5L, "1")).hasMessageContaining("owner's email");
        verifyNoInteractions(tenantService);
    }
}
