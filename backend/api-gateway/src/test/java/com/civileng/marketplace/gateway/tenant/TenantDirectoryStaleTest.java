package com.civileng.marketplace.gateway.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

/** When a cached host's entry expires: an outage keeps serving the last answer; a "no" does not. */
class TenantDirectoryStaleTest {

    private static final String ACME = "{\"tenantKey\":\"acme\",\"status\":\"ACTIVE\",\"modules\":[]}";
    private final Deque<ClientResponse> answers = new ArrayDeque<>();
    private final TenantDirectory directory = new TenantDirectory(
            WebClient.builder().exchangeFunction(req -> answers.isEmpty() ? Mono.error(new IllegalStateException("down"))
                    : Mono.just(answers.pop())), "http://tenant-service");

    private static ClientResponse ok(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    private void expireCache() {
        directory.clock = Clock.fixed(Instant.now().plus(Duration.ofMinutes(1)), ZoneOffset.UTC);
    }

    @Test
    void anOutageServesTheLastKnownTenant() {
        answers.add(ok(ACME));
        assertThat(directory.resolve("www.acme.test").block().getTenantKey()).isEqualTo("acme");
        expireCache();
        answers.add(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        assertThat(directory.resolve("www.acme.test").block().getTenantKey()).isEqualTo("acme");
    }

    @Test
    void aHostNoTenantServesAnyMoreStopsRouting() {
        answers.add(ok(ACME));
        assertThat(directory.resolve("www.acme.test").block()).isNotNull();
        expireCache();
        answers.add(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        assertThat(directory.resolve("www.acme.test").block()).isNull();
        // and it is forgotten: an outage now does not bring the old answer back
        assertThat(directory.resolve("www.acme.test").block()).isNull();
    }
}
