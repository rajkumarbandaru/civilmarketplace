package com.civileng.marketplace.procurement.controller;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.OrganizationDtos.*;
import com.civileng.marketplace.procurement.model.Capability;
import com.civileng.marketplace.procurement.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Organizations and whom they trade with. Identity comes from the gateway's signed
 * {@code X-User-Id} / {@code X-User-Email}; what the caller may do follows from membership.
 */
@RestController
@RequestMapping("/api/v1/procurement/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "B2B organizations, members and relationships")
public class OrganizationController {

    private final OrganizationService service;

    @GetMapping("/mine")
    @Operation(summary = "The organizations the caller acts for")
    public List<OrganizationView> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                       @RequestHeader(value = "X-User-Email", required = false) String email) {
        return service.mine(new Actor(userId, email));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an organization; the caller becomes its owner")
    public OrganizationView create(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestHeader(value = "X-User-Email", required = false) String email,
                                   @Valid @RequestBody OrganizationRequest request) {
        return service.create(new Actor(userId, email), request);
    }

    @GetMapping("/directory")
    @Operation(summary = "Organizations with a capability, as one of the caller's organizations sees them")
    public List<DirectoryEntry> directory(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Email", required = false) String email,
                                          @RequestParam(required = false) Capability capability,
                                          @RequestParam(required = false) Long asOrg) {
        return service.directory(new Actor(userId, email), capability, asOrg);
    }

    @GetMapping("/{orgId}")
    public OrganizationDetail get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                  @RequestHeader(value = "X-User-Email", required = false) String email,
                                  @PathVariable Long orgId) {
        return service.get(new Actor(userId, email), orgId);
    }

    @PutMapping("/{orgId}")
    public OrganizationDetail update(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @RequestHeader(value = "X-User-Email", required = false) String email,
                                     @PathVariable Long orgId, @Valid @RequestBody OrganizationRequest request) {
        return service.update(new Actor(userId, email), orgId, request);
    }

    @PostMapping("/{orgId}/members")
    public OrganizationDetail addMember(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @RequestHeader(value = "X-User-Email", required = false) String email,
                                        @PathVariable Long orgId, @Valid @RequestBody MemberRequest request) {
        return service.addMember(new Actor(userId, email), orgId, request);
    }

    @DeleteMapping("/{orgId}/members/{memberId}")
    public OrganizationDetail removeMember(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @RequestHeader(value = "X-User-Email", required = false) String email,
                                           @PathVariable Long orgId, @PathVariable Long memberId) {
        return service.removeMember(new Actor(userId, email), orgId, memberId);
    }

    @PutMapping("/{orgId}/relationships")
    @Operation(summary = "Mark another organization preferred or blocked")
    public OrganizationDetail setRelationship(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                              @RequestHeader(value = "X-User-Email", required = false) String email,
                                              @PathVariable Long orgId, @Valid @RequestBody RelationshipRequest request) {
        return service.setRelationship(new Actor(userId, email), orgId, request);
    }

    @DeleteMapping("/{orgId}/relationships/{relationshipId}")
    public OrganizationDetail removeRelationship(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                 @RequestHeader(value = "X-User-Email", required = false) String email,
                                                 @PathVariable Long orgId, @PathVariable Long relationshipId) {
        return service.removeRelationship(new Actor(userId, email), orgId, relationshipId);
    }
}
