package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.dto.MediaDtos;
import com.civileng.marketplace.user.model.WorkerPortfolio;
import com.civileng.marketplace.user.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Photos of a worker's past work")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    @Operation(summary = "My portfolio")
    public ResponseEntity<List<WorkerPortfolio>> mine(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(portfolioService.list(userId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "A worker's portfolio, as customers see it")
    public ResponseEntity<List<WorkerPortfolio>> ofUser(@PathVariable Long userId) {
        return ResponseEntity.ok(portfolioService.list(userId));
    }

    @PostMapping
    @Operation(summary = "Add an uploaded PORTFOLIO photo to my portfolio")
    public ResponseEntity<WorkerPortfolio> add(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody MediaDtos.PortfolioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioService.add(userId, request));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove an item from my portfolio")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") Long userId, @PathVariable Long itemId) {
        portfolioService.delete(userId, itemId);
        return ResponseEntity.noContent().build();
    }
}
