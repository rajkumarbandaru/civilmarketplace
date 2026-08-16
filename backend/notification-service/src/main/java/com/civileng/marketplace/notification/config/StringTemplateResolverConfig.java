package com.civileng.marketplace.notification.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * The engine that renders transactional email, able to resolve a template either from the
 * classpath or from a String — the latter being how admin-edited bodies stored in
 * {@code email_templates.html_body} reach Thymeleaf.
 *
 * <p>This is a whole engine rather than an extra resolver bean added to Boot's because resolver
 * order is the entire mechanism here and Boot's autoconfigured resolver leaves its order null,
 * which Thymeleaf sorts <em>last</em>. A string resolver added alongside it therefore wins every
 * lookup, including the {@code email/_layout} fragment reference inside a database body — which
 * it then treats as literal template content and fails to find a fragment in.
 *
 * <p>With both orders set explicitly, a name that exists as a file (every fragment reference)
 * resolves from the classpath, and only a name that does not — i.e. a whole HTML document, or a
 * subject line, passed as the template name — falls through to be treated as content. That is
 * what lets a database-stored body keep using the shipped layout.
 *
 * <p>A {@link SpringTemplateEngine}, not a bare {@code TemplateEngine}: the plain engine evaluates
 * expressions with OGNL, which is not on this service's classpath, so every {@code ${...}} would
 * fail with a NoClassDefFoundError at render time. Declaring it also makes Boot's own engine back
 * off, leaving this the single engine — which is correct, because it resolves everything Boot's
 * did, from the same {@code classpath:/templates/}.
 */
@Configuration
public class StringTemplateResolverConfig {

    @Bean
    public SpringTemplateEngine emailTemplateEngine(ApplicationContext applicationContext) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(classpathResolver(applicationContext));
        engine.addTemplateResolver(stringResolver());
        return engine;
    }

    /** Same location and settings as Boot's own resolver, so classpath templates render unchanged. */
    private SpringResourceTemplateResolver classpathResolver(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        // Without this the resolver claims every name, and nothing ever reaches the string
        // resolver below.
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        resolver.setOrder(1);
        return resolver;
    }

    /**
     * Caching is off because the cache key would be the entire body: a hit is near-impossible and
     * the misses would pin every previewed draft in memory.
     */
    private StringTemplateResolver stringResolver() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        resolver.setOrder(2);
        return resolver;
    }
}
