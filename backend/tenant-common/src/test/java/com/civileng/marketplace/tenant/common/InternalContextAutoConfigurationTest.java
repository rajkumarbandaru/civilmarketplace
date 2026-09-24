package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalContextAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InternalContextAutoConfiguration.class));

    @Test
    void guardsAServiceEvenWithTheTenantRuntimeOff() {
        // tenant-service's configuration: the registry is not tenant-scoped, but must still refuse
        // unsigned operator headers.
        runner.withPropertyValues("platform.tenant.enabled=false",
                        "platform.internal.signing-key=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    FilterRegistrationBean<?> reg = ctx.getBean("internalContextFilter", FilterRegistrationBean.class);
                    assertThat(reg.isEnabled()).isTrue();
                    assertThat(reg.getFilter()).isInstanceOf(InternalContextFilter.class);
                });
    }

    @Test
    void refusesToStartWithoutAKey() {
        runner.run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("INTERNAL_SIGNING_KEY"));
    }

    @Test
    void canBeSwitchedOffExplicitly() {
        runner.withPropertyValues("platform.internal.signature.enabled=false").run(ctx ->
                assertThat(ctx.getBean("internalContextFilter", FilterRegistrationBean.class).isEnabled()).isFalse());
    }
}
