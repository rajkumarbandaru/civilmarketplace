package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.dto.IntegrationDtos.IntegrationView;
import com.civileng.marketplace.tenant.service.TenantIntegrationService;
import com.civileng.marketplace.web.common.PlatformExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIntegrationControllerTest {

    private final TenantIntegrationService service = mock(TenantIntegrationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TenantIntegrationController(service))
                .setControllerAdvice(new PlatformExceptionHandler())
                .build();
    }

    @Test
    void aCustomerTenantsSuperAdminCannotReadAnotherTenantsIntegrations() throws Exception {
        mvc.perform(get("/api/v1/tenants/bhoomi/integrations")
                        .header("X-Tenant-Id", "acme").header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isForbidden());
        verify(service, never()).list(any());
    }

    @Test
    void anOperatorAdminWhoIsNotSuperAdminCannotWrite() throws Exception {
        mvc.perform(put("/api/v1/tenants/acme/integrations/payment")
                        .header("X-Tenant-Id", "platform").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theOperatorSuperAdminSeesHintsNeverSecrets() throws Exception {
        when(service.list("acme")).thenReturn(List.of(new IntegrationView("payment", true, "BYO",
                "razorpay", true, Map.of("keyId", "rzp_live_acme"), Map.of("keySecret", "••••3456"),
                "/webhooks/payments/razorpay/abc", "7", null)));

        mvc.perform(get("/api/v1/tenants/acme/integrations")
                        .header("X-Tenant-Id", "platform").header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].secretHints.keySecret").value("••••3456"))
                .andExpect(jsonPath("$[0].secrets").doesNotExist());
    }

    @Test
    void validationErrorsAreBadRequests() throws Exception {
        when(service.save(eq("acme"), eq("payment"), any(), any()))
                .thenThrow(new IllegalArgumentException("payment is missing secret [webhookSecret]"));

        mvc.perform(put("/api/v1/tenants/acme/integrations/payment")
                        .header("X-Tenant-Id", "platform").header("X-User-Role", "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BYO\",\"provider\":\"razorpay\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("webhookSecret")));
    }
}
