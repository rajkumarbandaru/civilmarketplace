package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * Account lookups for other services, in the caller's own workspace. Internal-only at the
 * gateway, and only on behalf of a signed-in caller (the signed context names one) — so it answers
 * a service acting for a member, never an anonymous probe.
 *
 * <p>procurement-service uses it to link a colleague added by email to their account straight
 * away, so they get in-app notices before they have ever opened procurement.
 */
@RestController
@RequestMapping("/api/v1/auth/internal")
@RequiredArgsConstructor
@Tag(name = "Internal accounts", description = "Service-to-service account lookups")
public class InternalAccountController {

    private final UserRepository users;

    public record AccountRef(Long id, String email, String name) { }

    @GetMapping("/accounts/by-email")
    @Operation(summary = "The active account with this email in the current workspace")
    public AccountRef byEmail(@RequestHeader(value = "X-User-Id", required = false) Long callerId,
                              @RequestParam String email) {
        if (callerId == null) {
            throw new UnauthenticatedException("A signed-in caller is required");
        }
        return users.findByEmailAndIsDeletedFalse(email.trim().toLowerCase())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> new AccountRef(u.getId(), u.getEmail(), u.getName()))
                .orElseThrow(() -> new NoSuchElementException("No such account"));
    }
}
