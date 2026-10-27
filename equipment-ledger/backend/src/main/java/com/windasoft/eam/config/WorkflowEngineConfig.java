package com.windasoft.eam.config;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WorkflowEngineConfig {
  @Bean(destroyMethod = "close")
  @DependsOnDatabaseInitialization
  public ProcessEngine workflowEngine(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      com.windasoft.eam.service.workflow.WorkflowRoutingListener routing,
      com.windasoft.eam.service.workflow.WorkflowScriptDelegate scripts) {
    var config = new SpringProcessEngineConfiguration();
    config.setDataSource(dataSource);
    config.setTransactionManager(transactionManager);
    config.setDatabaseSchemaUpdate("false");
    config.setDisableIdmEngine(true);
    config.setDisableEventRegistry(true);
    config.setAsyncExecutorActivate(false);
    config.setBeans(java.util.Map.of("eamWorkflowRouting", routing, "eamWorkflowScript", scripts));
    return config.buildProcessEngine();
  }
}
