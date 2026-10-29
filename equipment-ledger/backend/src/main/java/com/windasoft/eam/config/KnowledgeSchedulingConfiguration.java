package com.windasoft.eam.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration
public class KnowledgeSchedulingConfiguration {
  @Bean(name="knowledgeTaskScheduler") public ThreadPoolTaskScheduler scheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("knowledge-");return scheduler;}
}
