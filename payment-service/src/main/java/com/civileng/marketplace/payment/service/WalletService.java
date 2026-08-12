package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.payment.model.Wallet;
import com.civileng.marketplace.payment.model.WalletTransaction;
import com.civileng.marketplace.payment.model.WalletTransactionType;
import com.civileng.marketplace.payment.repository.WalletRepository;
import com.civileng.marketplace.payment.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    /** Wallets are created on first use rather than at registration — nothing to reconcile. */
    @Transactional
    public Wallet getOrCreate(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wallet wallet = walletRepository.save(Wallet.builder().userId(userId).build());
                    log.info("Wallet {} created for user {}", wallet.getId(), userId);
                    return wallet;
                });
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No wallet for this user yet"));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactions(Long userId, Pageable pageable) {
        Wallet wallet = getWallet(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
    }

    /**
     * Credits a wallet and writes the matching ledger line in the same transaction — a balance
     * change without its ledger row would make commission unauditable, which CP·06 calls out
     * specifically ("auditable to the paisa").
     */
    @Transactional
    public Wallet credit(Long userId, BigDecimal amount, WalletTransactionType type,
                         String description, String referenceType, Long referenceId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        Wallet wallet = getOrCreate(userId);
        BigDecimal before = wallet.getBalance();
        BigDecimal after = before.add(amount);

        wallet.setBalance(after);
        wallet.setTotalEarned(wallet.getTotalEarned().add(amount));
        Wallet saved = walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(saved.getId())
                .transactionType(type)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .description(description)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build());

        log.info("Wallet {} credited {} ({}), balance {} -> {}",
                saved.getId(), amount, type, before, after);
        return saved;
    }

    /**
     * FR-09's payout hold: moves money out of the withdrawable balance without removing it from
     * the user's wallet, so an open dispute freezes it rather than confiscating it.
     */
    @Transactional
    public Wallet hold(Long userId, BigDecimal amount, String description, Long referenceId) {
        Wallet wallet = getWallet(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Balance is lower than the amount to hold");
        }
        BigDecimal before = wallet.getBalance();
        wallet.setBalance(before.subtract(amount));
        wallet.setHeldBalance(wallet.getHeldBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(saved.getId())
                .transactionType(WalletTransactionType.HOLD)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(saved.getBalance())
                .description(description)
                .referenceType("ESCROW")
                .referenceId(referenceId)
                .build());
        log.info("Wallet {} held {} pending dispute resolution", saved.getId(), amount);
        return saved;
    }

    @Transactional
    public Wallet releaseHold(Long userId, BigDecimal amount, String description, Long referenceId) {
        Wallet wallet = getWallet(userId);
        if (wallet.getHeldBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Held balance is lower than the amount to release");
        }
        BigDecimal before = wallet.getBalance();
        wallet.setHeldBalance(wallet.getHeldBalance().subtract(amount));
        wallet.setBalance(before.add(amount));
        Wallet saved = walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(saved.getId())
                .transactionType(WalletTransactionType.HOLD_RELEASE)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(saved.getBalance())
                .description(description)
                .referenceType("ESCROW")
                .referenceId(referenceId)
                .build());
        return saved;
    }
}
