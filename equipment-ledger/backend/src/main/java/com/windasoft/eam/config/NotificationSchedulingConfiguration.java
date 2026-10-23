package com.windasoft.eam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificationSchedulingConfiguration {
  // Preserve the existing single-thread scheduler for business generation/cleanup jobs.
  @Bean(name="taskScheduler")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name="taskScheduler")
  public ThreadPoolTaskScheduler taskScheduler() {
    var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("scheduling-");return scheduler;
  }
  @Bean(name="notificationTaskScheduler")
  public ThreadPoolTaskScheduler notificationTaskScheduler() {
    var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("notification-");return scheduler;
  }
}
