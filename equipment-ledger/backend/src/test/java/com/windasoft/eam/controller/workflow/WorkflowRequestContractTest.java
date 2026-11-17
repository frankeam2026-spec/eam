package com.windasoft.eam.controller.workflow;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Setting;
import com.windasoft.eam.service.workflow.WorkflowRuntimeService;
import com.windasoft.eam.service.workflow.WorkflowTemplateService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 在真正HTTP绑定边界验证：坏请求不得调用审批业务，不能用Service直接调用绕过反序列化缺陷。 */
class WorkflowRequestContractTest {
  private WorkflowRuntimeService runtime;
  private WorkflowTemplateService templates;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    runtime = mock(WorkflowRuntimeService.class);
    templates = mock(WorkflowTemplateService.class);
    when(runtime.act(anyLong(), anyLong(), anyLong(), anyBoolean(), nullable(String.class)))
        .thenReturn(List.of());
    when(runtime.withdraw(anyLong(), anyLong())).thenReturn(List.of());
    when(templates.approvalSetting(anyLong(), anyBoolean(), anyLong()))
        .thenReturn(new Setting(BooleanNode.TRUE, 0L, BooleanNode.FALSE));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new WorkflowInstanceController(runtime),
                new WorkflowTemplateController(
                    templates, new ObjectMapper().findAndRegisterModules()))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  void templateUpdateRequiresAnExactVersion() throws Exception {
    for (String body :
        List.of(
            "{\"id\":\"12\",\"companyId\":\"1\"}",
            "{\"id\":\"12\",\"companyId\":\"1\",\"rowVersion\":1.9}")) {
      mvc.perform(
              post("/api/v1/workflow/templates")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(templates);
  }

  @Test
  void decisionAndVersionAreRequiredBeforeAnyBusinessCall() throws Exception {
    for (String body :
        List.of(
            """
            {"approvalId":"12","rowVersion":0}
            """,
            """
            {"approvalId":"12","approve":true}
            """,
            """
            {"rowVersion":0,"approve":true}
            """,
            """
            {"approvalId":"12","rowVersion":null,"approve":true}
            """)) {
      mvc.perform(
              post("/api/v1/workflow/instances/1/act")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(runtime);
  }

  @Test
  void fractionalIdsAndVersionsCannotBeSilentlyTruncated() throws Exception {
    for (String body :
        List.of(
            """
            {"approvalId":12.9,"rowVersion":0,"approve":true}
            """,
            """
            {"approvalId":"12","rowVersion":1.9,"approve":true}
            """)) {
      mvc.perform(
              post("/api/v1/workflow/instances/1/act")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(runtime);
  }

  @Test
  void settingRequiresExplicitBooleanAndPreservesInitialMinusOneVersion() throws Exception {
    mvc.perform(
            put("/api/v1/workflow/templates/repair-approval-setting")
                .param("companyId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rowVersion":2}
                    """))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/workflow/templates/repair-approval-setting")
                .param("companyId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"required":true,"rowVersion":-1}
                    """))
        .andExpect(status().isOk());
    verify(templates).approvalSetting(1L, true, -1L);
  }

  @Test
  void withdrawalCannotDefaultMissingVersionToZero() throws Exception {
    mvc.perform(
            post("/api/v1/workflow/instances/1/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(runtime);
  }

  @Test
  void bigintIdsAndVersionRemainExactBeyondIntegerRange() throws Exception {
    mvc.perform(
            post("/api/v1/workflow/instances/1/act")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"approvalId":"9007199254740993","rowVersion":5000000000,"approve":true,"opinion":"同意"}
"""))
        .andExpect(status().isOk());
    verify(runtime).act(1L, 9007199254740993L, 5000000000L, true, "同意");
  }
}
