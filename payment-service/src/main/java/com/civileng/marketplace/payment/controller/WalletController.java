package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.model.Wallet;
import com.civileng.marketplace.payment.model.WalletTransaction;
import com.civileng.marketplace.payment.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * The `wallets` table has existed since payment-service's first migration but had no API. Escrow
 * release is the first thing that puts money in one, so it gets a read surface here.
 *
 * <p>Withdrawal (CP·06 FR-05) is deliberately absent — it needs PSP payouts plus the KYC-approved
 * gate, and neither is in this module's scope.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Supply-side wallet balances and ledger")
public class WalletController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "The caller's wallet")
    public ResponseEntity<Wallet> myWallet(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(walletService.getOrCreate(userId));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "The caller's wallet ledger")
    public ResponseEntity<Page<WalletTransaction>> myTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactions(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Any user's wallet (admin only)")
    public ResponseEntity<Wallet> userWallet(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long userId) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new com.civileng.marketplace.payment.exception.AccessDeniedException(
                    "Admin role required");
        }
        return ResponseEntity.ok(walletService.getWallet(userId));
    }
}
