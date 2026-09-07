package com.civileng.marketplace.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
// This service's only Feign client is the shared UserNameClient — booking-service and
// payment-service each used to declare an identical copy of it in their own client package,
// which is now empty and gone.
@EnableFeignClients(basePackages = "com.civileng.marketplace.web.common.client")
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
