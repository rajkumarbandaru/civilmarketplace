package com.civileng.marketplace.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected rather than read statically, so time-based code (TOTP) can be tested at fixed instants. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
