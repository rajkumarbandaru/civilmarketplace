package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTaskDecoratorTest {

    private final TenantTaskDecorator decorator = new TenantTaskDecorator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        executor.shutdownNow();
    }

    @Test
    void asyncWorkRunsAsTheSubmittingTenantAndLeavesThePoolThreadClean() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        TenantContext.set("bhoomi");

        CompletableFuture.runAsync(decorator.decorate(() -> seen.set(TenantContext.get())), executor).get();
        String afterwards = CompletableFuture.supplyAsync(TenantContext::get, executor).get();

        assertThat(seen.get()).isEqualTo("bhoomi");
        assertThat(afterwards).isNull();
    }

    @Test
    void untenantedSubmissionsStayUntenanted() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("unset");

        CompletableFuture.runAsync(decorator.decorate(() -> seen.set(TenantContext.get())), executor).get();

        assertThat(seen.get()).isNull();
    }
}
