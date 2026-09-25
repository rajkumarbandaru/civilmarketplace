package com.civileng.marketplace.tenant.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An HTTP-01 answer: what the ACME server must find at /.well-known/acme-challenge/{token}. */
@Entity
@Table(name = "acme_challenges")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AcmeChallenge {

    @Id
    @Column(length = 128)
    private String token;

    @Column(nullable = false, length = 253)
    private String host;

    @Column(name = "key_authorization", nullable = false, length = 512)
    private String keyAuthorization;
}
