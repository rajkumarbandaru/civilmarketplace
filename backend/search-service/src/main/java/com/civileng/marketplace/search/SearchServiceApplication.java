package com.civileng.marketplace.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
// UserDirectoryClient is shared with notification-service, which pages the same endpoint.
@EnableFeignClients(basePackages = {
        "com.civileng.marketplace.search.client",
        "com.civileng.marketplace.web.common.client"
})
@EnableScheduling
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
