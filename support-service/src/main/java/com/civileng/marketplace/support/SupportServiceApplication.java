package com.civileng.marketplace.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class SupportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
