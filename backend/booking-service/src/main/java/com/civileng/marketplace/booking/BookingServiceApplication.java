package com.civileng.marketplace.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
// This service's only Feign client is the shared UserNameClient — booking-service and
// payment-service each used to declare an identical copy of it in their own client package,
// which is now empty and gone.
@EnableFeignClients(basePackages = "com.civileng.marketplace.web.common.client")
@EnableJpaAuditing
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
