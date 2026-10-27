package com.windasoft.eam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EamApplication {
  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("--workflow-script-worker")) {
      com.windasoft.eam.common.workflow.WorkflowScriptWorker.main(args);
      return;
    }
    SpringApplication.run(EamApplication.class, args);
  }
}
