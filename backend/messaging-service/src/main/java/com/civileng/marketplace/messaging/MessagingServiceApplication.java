package com.civileng.marketplace.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
// This service's only Feign client is the shared BookingLookupClient — this service and
// review-service used to declare an identical copy each, in their own client package. Both are gone.
@EnableFeignClients(basePackages = "com.civileng.marketplace.web.common.client")
public class MessagingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingServiceApplication.class, args);
    }
}
