package com.civileng.marketplace.web.common;

import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the shared HTTP-layer pieces into whichever service puts {@code web-common} on its
 * classpath. Everything here is either conditional on a property or on a class being present, so
 * adding the dependency to a service that wants only one of the three changes nothing else.
 */
@Configuration
@EnableConfigurationProperties(WebCommonProperties.class)
@Slf4j
public class WebCommonAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.web", name = "error-handler",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public PlatformExceptionHandler platformExceptionHandler() {
        return new PlatformExceptionHandler();
    }

    /**
     * Both optional pieces live in nested classes carrying a <em>class-level</em>
     * {@code @ConditionalOnClass}, not on a {@code @Bean} method.
     *
     * <p>The difference matters and is not cosmetic. A method-level condition is evaluated only
     * after Spring reflects over the configuration class, and reflecting over it resolves every
     * {@code @Bean} method's return type — so a return type whose supertype is absent throws
     * {@code ClassNotFoundException} before the condition it was guarded by ever runs. A
     * class-level condition is checked from the ASM metadata first, and the class is never loaded
     * when it fails. tenant-service, which has no Feign, failed to start on exactly this.
     */
    @Configuration
    @ConditionalOnClass(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ConditionalOnProperty(prefix = "platform.web", name = "error-handler",
            havingValue = "true", matchIfMissing = true)
    public static class OptimisticLockConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OptimisticLockExceptionHandler optimisticLockExceptionHandler() {
            return new OptimisticLockExceptionHandler();
        }
    }

    /** Only where Feign is, so a service with no outbound calls starts without it. */
    @Configuration
    @ConditionalOnClass(RequestInterceptor.class)
    public static class IdentityPropagationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public IdentityFeignInterceptor identityFeignInterceptor() {
            return new IdentityFeignInterceptor();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "platform.web.admin-guard", name = "enabled",
            havingValue = "true")
    @RequiredArgsConstructor
    @Slf4j
    public static class AdminGuardConfiguration implements WebMvcConfigurer {

        private final WebCommonProperties properties;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            var guard = properties.getAdminGuard();
            log.info("Staff-role gate active on {} (exempt: {})",
                    guard.getPathPatterns(), guard.getExcludePathPatterns());
            registry.addInterceptor(new AdminRoleInterceptor())
                    .addPathPatterns(guard.getPathPatterns())
                    .excludePathPatterns(guard.getExcludePathPatterns());
        }
    }
}
