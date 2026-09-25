package com.civileng.marketplace.tenant.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "acme_accounts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AcmeAccountKey {

    @Id
    @Column(length = 255)
    private String directory;

    @Column(name = "key_pem", nullable = false, columnDefinition = "TEXT")
    private String keyPem;
}
