package com.civileng.marketplace.tenant.config;

import com.civileng.marketplace.tenant.common.TenantTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TenantTopicConfig {

    /**
     * Created explicitly rather than left to auto-creation: every tenanted service subscribes to
     * this topic at startup, and a service that boots before the first tenant is ever created
     * would otherwise be subscribing to a topic that does not exist.
     */
    @Bean
    public NewTopic tenantEventsTopic() {
        return TopicBuilder.name(TenantTopics.TENANT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
