package com.civileng.marketplace.analytics.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WarehouseConfig {

    @Bean
    public Pseudonyms pseudonyms(@Value("${analytics.pseudonym-key:}") String key) {
        return new Pseudonyms(key);
    }

    @Bean
    public Projector projector(JdbcTemplate jdbc, Pseudonyms pseudonyms) {
        return new Projector(jdbc, pseudonyms);
    }
}
