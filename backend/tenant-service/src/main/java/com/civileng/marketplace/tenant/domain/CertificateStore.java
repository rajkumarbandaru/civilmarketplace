package com.civileng.marketplace.tenant.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Where the edge finds each custom domain's certificate: {@code <host>.crt} (chain) and
 * {@code <host>.key}, on a volume shared with nginx, which picks them by SNI at handshake time —
 * no reload per certificate. Written atomically so a handshake never reads half a file.
 */
@Slf4j
@Component
public class CertificateStore {

    private final Path dir;

    /**
     * The group the edge's workers run as (nginx: 101). Certificates load per handshake, in the
     * workers, so the files are given that group — the key stays unreadable to anyone else (0640).
     */
    private final int edgeGid;

    public CertificateStore(DomainProperties props,
                            @org.springframework.beans.factory.annotation.Value("${platform.domains.edge-gid:101}") int edgeGid) {
        this.dir = Paths.get(props.certDirectory());
        this.edgeGid = edgeGid;
    }

    public void write(String host, String chainPem, String keyPem) throws IOException {
        Files.createDirectories(dir);
        atomic(dir.resolve(host + ".key"), keyPem, "rw-r-----", edgeGid);
        atomic(dir.resolve(host + ".crt"), chainPem, "rw-r--r--", edgeGid);
        log.info("Certificate for {} written for the edge", host);
    }

    public void remove(String host) throws IOException {
        Files.deleteIfExists(dir.resolve(host + ".crt"));
        Files.deleteIfExists(dir.resolve(host + ".key"));
    }

    private static void atomic(Path target, String content, String perms, int gid) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".tmp-", ".pem");
        Files.writeString(tmp, content);
        try {
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString(perms));
            Files.setAttribute(tmp, "unix:gid", gid);
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            // non-POSIX file system (tests on some platforms)
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
