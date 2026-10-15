package com.iflytek.skillhub.config;

import com.iflytek.skillhub.infra.llm.KnowledgeSummaryProperties;
import com.iflytek.skillhub.service.knowledge.KnowledgeTextProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Extraction and summaries each get one thread, off the application's maintenance scheduler. */
@Configuration
@EnableConfigurationProperties({KnowledgeTextProperties.class, KnowledgeSummaryProperties.class})
public class KnowledgeTextConfig {
    @Bean("knowledgeTextScheduler")
    public ThreadPoolTaskScheduler knowledgeTextScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("knowledge-text-");
        return scheduler;
    }

    @Bean("knowledgeSummaryScheduler")
    public ThreadPoolTaskScheduler knowledgeSummaryScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("knowledge-summary-");
        return scheduler;
    }
}
