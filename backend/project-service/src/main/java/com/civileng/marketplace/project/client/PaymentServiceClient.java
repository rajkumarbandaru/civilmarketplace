package com.civileng.marketplace.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "payment-service", path = "/api/v1/escrow",
        fallbackFactory = PaymentServiceClientFallbackFactory.class)
public interface PaymentServiceClient {

    @GetMapping("/project/{projectId}")
    List<EscrowDto> getProjectEscrow(@PathVariable("projectId") Long projectId);
}
