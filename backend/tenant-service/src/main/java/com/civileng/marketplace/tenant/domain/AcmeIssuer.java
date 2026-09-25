package com.civileng.marketplace.tenant.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Obtains a certificate for a host from the ACME server (RFC 8555) with an HTTP-01 challenge: the
 * answer is stored for {@link AcmeChallengeController} to serve at the host's
 * /.well-known/acme-challenge/, which the edge forwards here once the host points at us.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcmeIssuer {

    public record Issued(String chainPem, String keyPem, X509Certificate certificate) { }

    private final DomainProperties props;
    private final AcmeChallengeRepository challenges;
    private final AcmeAccountKeyRepository accountKeys;
    private final TransactionTemplate tx;

    public Issued issue(String host) throws Exception {
        Session session = new Session(props.acmeDirectory());
        session.networkSettings().setTimeout(Duration.ofSeconds(30));
        Account account = new AccountBuilder()
                .agreeToTermsOfService()
                .addEmail(props.contactEmail())
                .useKeyPair(accountKey())
                .create(session);
        Order order = account.newOrder().domain(host).create();
        try {
            for (Authorization auth : order.getAuthorizations()) {
                if (auth.getStatus() == Status.VALID) {
                    continue;
                }
                Http01Challenge challenge = auth.findChallenge(Http01Challenge.class)
                        .orElseThrow(() -> new AcmeException("The ACME server offers no http-01 challenge for " + host));
                tx.executeWithoutResult(s -> challenges.save(new AcmeChallenge(challenge.getToken(), host,
                        challenge.getAuthorization())));
                challenge.trigger();
                Status status = auth.waitForCompletion(Duration.ofSeconds(90));
                if (status != Status.VALID) {
                    throw new AcmeException("Validation of " + host + " failed: "
                            + challenge.getError().map(Object::toString).orElse(status.name()));
                }
            }
            KeyPair domainKey = KeyPairUtils.createKeyPair(2048);
            order.waitUntilReady(Duration.ofSeconds(60));
            order.execute(domainKey);
            Status done = order.waitForCompletion(Duration.ofSeconds(90));
            if (done != Status.VALID) {
                throw new AcmeException("Order for " + host + " ended " + done + ": "
                        + order.getError().map(Object::toString).orElse(""));
            }
            Certificate cert = order.getCertificate();
            StringWriter chain = new StringWriter();
            cert.writeCertificate(chain);
            StringWriter key = new StringWriter();
            KeyPairUtils.writeKeyPair(domainKey, key);
            return new Issued(chain.toString(), key.toString(), cert.getCertificate());
        } finally {
            tx.executeWithoutResult(s -> challenges.deleteByHost(host));
        }
    }

    /** The platform's account key for this ACME directory, made once and kept. */
    private KeyPair accountKey() throws Exception {
        AcmeAccountKey stored = tx.execute(s -> accountKeys.findById(props.acmeDirectory()).orElse(null));
        if (stored != null) {
            return KeyPairUtils.readKeyPair(new StringReader(stored.getKeyPem()));
        }
        KeyPair pair = KeyPairUtils.createKeyPair(2048);
        StringWriter pem = new StringWriter();
        KeyPairUtils.writeKeyPair(pair, pem);
        tx.executeWithoutResult(s -> accountKeys.save(new AcmeAccountKey(props.acmeDirectory(), pem.toString())));
        return pair;
    }
}
