package com.iflytek.skillhub.config;

import com.iflytek.skillhub.infra.llm.SkillIntroductionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(SkillIntroductionProperties.class)
public class SkillIntroductionConfig {
    // Keep the existing application's maintenance jobs off the inference scheduler.
    @Bean("taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(TaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean("skillIntroductionScheduler")
    public ThreadPoolTaskScheduler skillIntroductionScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("skill-introduction-");
        return scheduler;
    }
}
