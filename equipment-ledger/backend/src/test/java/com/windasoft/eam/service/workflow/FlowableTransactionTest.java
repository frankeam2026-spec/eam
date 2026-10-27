package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Embedded compatibility/atomicity proof. Never connects to an application database. */
class FlowableTransactionTest {
  @Test
  void businessWriteAndEngineStartShareRollback() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:wf_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    var manager = new DataSourceTransactionManager(ds);
    var tx = new TransactionTemplate(manager);
    var db = new JdbcTemplate(ds);
    db.execute("CREATE TABLE proof(id BIGINT PRIMARY KEY)");
    var config = new SpringProcessEngineConfiguration();
    config.setDataSource(ds);
    config.setTransactionManager(manager);
    config.setDatabaseSchemaUpdate("true");
    config.setAsyncExecutorActivate(false);
    var engine = config.buildProcessEngine();
    try {
      String xml =
          """
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="eam.workflow">
  <process id="proof" isExecutable="true">
    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="approve"/>
    <userTask id="approve" name="审批"/>
    <sequenceFlow id="s2" sourceRef="approve" targetRef="end"/><endEvent id="end"/>
  </process>
</definitions>
""";
      engine
          .getRepositoryService()
          .createDeployment()
          .tenantId("10001")
          .addString("proof.bpmn20.xml", xml)
          .deploy();
      assertThrows(
          IllegalStateException.class,
          () ->
              tx.execute(
                  s -> {
                    db.update("INSERT INTO proof VALUES(1)");
                    engine
                        .getRuntimeService()
                        .startProcessInstanceByKeyAndTenantId("proof", "10001");
                    throw new IllegalStateException("rollback proof");
                  }));
      assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM proof", Integer.class));
      assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
      tx.executeWithoutResult(
          s -> {
            db.update("INSERT INTO proof VALUES(2)");
            engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("proof", "10001");
          });
      assertEquals(1, engine.getTaskService().createTaskQuery().count());
      String task = engine.getTaskService().createTaskQuery().singleResult().getId();
      assertThrows(
          IllegalStateException.class,
          () ->
              tx.execute(
                  s -> {
                    engine.getTaskService().complete(task);
                    db.update("DELETE FROM proof");
                    throw new IllegalStateException("rollback approval");
                  }));
      assertEquals(1, engine.getTaskService().createTaskQuery().count());
      assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM proof", Integer.class));
    } finally {
      engine.close();
    }
  }
}
