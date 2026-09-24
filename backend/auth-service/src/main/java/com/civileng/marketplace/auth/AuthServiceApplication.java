package com.civileng.marketplace.auth;

import org.springframework.boot.SpringApplication;
import com.civileng.marketplace.web.common.client.MediaClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
// Only the media client: the other shared clients point at services this one has no need to call.
@EnableFeignClients(clients = MediaClient.class)
@EnableJpaAuditing
@EnableAsync
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
