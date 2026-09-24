package com.civileng.marketplace.admin.config;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/** Which version of a document is live in a scope. Moving it is what publishing means. */
@Entity
@Table(name = "config_pointers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigPointer {

    @EmbeddedId
    private Key id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(length = 60)
        private String scope;
        @Column(length = 20)
        private String document;
    }
}
