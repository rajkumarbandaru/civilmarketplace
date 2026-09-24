package com.civileng.marketplace.procurement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected rather than called statically, so tests can pin "now". */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
