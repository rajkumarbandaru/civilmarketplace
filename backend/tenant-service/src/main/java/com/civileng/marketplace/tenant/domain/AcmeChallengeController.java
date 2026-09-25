package com.civileng.marketplace.tenant.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the ACME server's HTTP-01 check. Public: the ACME server calls it through the edge,
 * over plain HTTP, on the host being certified. It reveals only a value the server already knows
 * half of, for a token it issued.
 */
@RestController
@RequiredArgsConstructor
public class AcmeChallengeController {

    private final AcmeChallengeRepository challenges;

    @GetMapping(value = "/.well-known/acme-challenge/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> answer(@PathVariable String token) {
        return challenges.findById(token)
                .map(c -> ResponseEntity.ok(c.getKeyAuthorization()))
                .orElse(ResponseEntity.notFound().build());
    }
}
