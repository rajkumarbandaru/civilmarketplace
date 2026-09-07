package com.civileng.marketplace.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
// UserDirectoryClient is shared with search-service, which pages the same endpoint.
@EnableFeignClients(basePackages = {
        "com.civileng.marketplace.notification.client",
        "com.civileng.marketplace.web.common.client"
})
@EnableAsync
// Drives AnnouncementReleaseJob, which sends announcements booked for a later time.
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
