package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.dto.workflow.WorkflowDefinition.Script;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkflowScriptRunnerTest {
  final WorkflowScriptRunner runner = new WorkflowScriptRunner();

  Script script(String source, String type) {
    return new Script("javascript", source, "computed_test", type);
  }

  @Test
  void exactDecimalAndGeneralJavascriptExecuteInTheWorker() {
    assertEquals(
        new BigDecimal("0.3"),
        runner.execute(
            script("return decimal.sum(input.lines.map(line => line.quantity));", "NUMBER"),
            Map.of(
                "lines",
                List.of(
                    Map.of("quantity", new BigDecimal("0.1")),
                    Map.of("quantity", new BigDecimal("0.2"))))));
    assertEquals(
        "true:true:true:true",
        runner.execute(
            script(
                "return [typeof Java === 'undefined',typeof require === 'undefined',typeof process"
                    + " === 'undefined',typeof fetch === 'undefined'].join(':');",
                "TEXT"),
            Map.of()));
  }

  @Test
  void cannotAccessHostClassesAndCannotReturnObjectsAsBusinessValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runner.execute(
                script("return Java.type('java.lang.System').getenv('DB_PASSWORD');", "TEXT"),
                Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> runner.execute(script("return {value: 'bad'};", "TEXT"), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> runner.execute(script("return 0.1 + 0.2;", "NUMBER"), Map.of()));
  }

  @Test
  void infiniteLoopIsTerminatedAndNextRequestStillWorks() {
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(15),
        () -> {
          assertTrue(
              assertThrows(
                      IllegalArgumentException.class,
                      () -> runner.execute(script("while(true) {}", "BOOLEAN"), Map.of()))
                  .getMessage()
                  .contains("超时"));
        });
    assertEquals(
        true,
        runner.execute(
            script("return input.allowed === true;", "BOOLEAN"), Map.of("allowed", true)));
  }

  @Test
  void publicationSyntaxCheckDoesNotExecuteTheScript() {
    assertDoesNotThrow(() -> runner.validate(script("while(true) {}", "BOOLEAN")));
    assertThrows(
        IllegalArgumentException.class, () -> runner.validate(script("return ( ;", "TEXT")));
  }

  @Test
  void excessiveAllocationCannotExhaustTheApplicationProcess() {
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(15),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    runner.execute(
                        script(
                            "const arrays=[];while(true)arrays.push(new Array(1000000).fill('x'));",
                            "BOOLEAN"),
                        Map.of())));
    assertEquals("healthy", runner.execute(script("return 'healthy';", "TEXT"), Map.of()));
  }
}
