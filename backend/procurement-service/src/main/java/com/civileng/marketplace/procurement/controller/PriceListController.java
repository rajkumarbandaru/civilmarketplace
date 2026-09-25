package com.civileng.marketplace.procurement.controller;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PriceListDtos.*;
import com.civileng.marketplace.procurement.service.PriceListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/procurement/price-lists")
@RequiredArgsConstructor
@Tag(name = "Price lists", description = "Supplier catalogues and buyer contracts")
public class PriceListController {

    private final PriceListService service;

    @GetMapping
    @Operation(summary = "Catalogues and contracts of the caller's organizations")
    public List<PriceListView> list(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @RequestHeader(value = "X-User-Email", required = false) String email) {
        return service.list(new Actor(userId, email));
    }

    @PutMapping("/catalogue")
    @Operation(summary = "Replace a supplier's standard catalogue")
    public PriceListView saveCatalogue(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                       @RequestHeader(value = "X-User-Email", required = false) String email,
                                       @Valid @RequestBody CatalogueRequest request) {
        return service.saveCatalogue(new Actor(userId, email), request);
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Offer a buyer a contract: rates, payment terms, credit limit")
    public PriceListView propose(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-User-Email", required = false) String email,
                                 @Valid @RequestBody ContractRequest request) {
        return service.propose(new Actor(userId, email), request);
    }

    @PostMapping("/contracts/{id}/accept")
    public PriceListView accept(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestHeader(value = "X-User-Email", required = false) String email,
                                @PathVariable Long id) {
        return service.accept(new Actor(userId, email), id);
    }

    @PostMapping("/contracts/{id}/decline")
    public PriceListView decline(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-User-Email", required = false) String email,
                                 @PathVariable Long id) {
        return service.decline(new Actor(userId, email), id);
    }

    @PostMapping("/contracts/{id}/terminate")
    public PriceListView terminate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestHeader(value = "X-User-Email", required = false) String email,
                                   @PathVariable Long id) {
        return service.terminate(new Actor(userId, email), id);
    }
}
