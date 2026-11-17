package com.windasoft.eam.controller.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.workflow.WorkflowExpression.Variable;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowSaveRequest;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.ChoicePage;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Company;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.ScriptResult;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Setting;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.TemplatePage;
import com.windasoft.eam.dto.workflow.WorkflowTrialStep;
import com.windasoft.eam.service.workflow.WorkflowTemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow/templates")
public class WorkflowTemplateController {
  private final WorkflowTemplateService service;
  private final ObjectMapper json;

  public WorkflowTemplateController(WorkflowTemplateService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  @GetMapping
  public TemplatePage list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long companyId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(q, companyId, page, size);
  }

  @GetMapping("/companies")
  public DataResponse<List<Company>> companies(@RequestParam(defaultValue = "查看") String action) {
    return new DataResponse<>(service.companies(action));
  }

  @GetMapping("/variables")
  public DataResponse<Collection<Variable>> variables(
      @RequestParam(defaultValue = "requisition") String businessType) {
    return new DataResponse<>(service.variables(businessType));
  }

  /** -1保留旧“尚无绑定行”的并发协议；缺失不能被当成0或关闭审批。 */
  public record ApprovalSetting(
      @NotNull(message = "{ui.equipment.text1086}") Boolean required,
      @NotNull(message = "{server.common.message0018}")
          @Min(-1)
          @JsonDeserialize(using = ExactLongDeserializer.class)
          Long rowVersion) {}

  @GetMapping("/repair-approval-setting")
  public DataResponse<Setting> approvalSetting(@RequestParam long companyId) {
    return new DataResponse<>(service.approvalSetting(companyId));
  }

  @PutMapping("/repair-approval-setting")
  public DataResponse<Setting> approvalSetting(
      @RequestParam long companyId, @Valid @RequestBody ApprovalSetting body) {
    return new DataResponse<>(
        service.approvalSetting(companyId, body.required(), body.rowVersion()));
  }

  @GetMapping("/choices")
  public ChoicePage choices(
      @RequestParam long companyId,
      @RequestParam String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.choices(companyId, kind, q, page, size);
  }

  @GetMapping("/{id}")
  public DataResponse<WorkflowDefinition> detail(
      @PathVariable long id, @RequestParam(required = false) Long versionId) {
    return new DataResponse<>(service.detail(id, versionId));
  }

  @GetMapping("/{id}/versions")
  public DataResponse<List<WorkflowTemplateViews.Version>> versions(@PathVariable long id) {
    return new DataResponse<>(service.versions(id));
  }

  @PostMapping
  public DataResponse<WorkflowDefinition> save(@Valid @RequestBody WorkflowSaveRequest body) {
    return new DataResponse<>(service.save(body.toDefinition()));
  }

  public record Version(
      @NotNull(message = "{server.common.message0018}")
          @PositiveOrZero
          @JsonDeserialize(using = ExactLongDeserializer.class)
          Long rowVersion) {}

  public record References(long companyId, String kind, List<Long> ids) {}

  @PostMapping("/resolve")
  public ChoicePage resolve(@RequestBody References body) {
    return service.resolve(body.companyId(), body.kind(), body.ids());
  }

  public record Expression(String expression, WorkflowDefinition definition) {}

  @PostMapping("/expression")
  public DataResponse<WorkflowTemplateViews.Expression> expression(@RequestBody Expression body) {
    return new DataResponse<>(
        WorkflowTemplateViews.Expression.from(service.parse(body.expression(), body.definition())));
  }

  /** 仅values是目录约束的动态变量对象；公司、脚本及流程定义都有固定类型。 */
  public record ScriptTrial(
      long companyId, WorkflowDefinition.Script script, @NotNull ObjectNode values) {}

  @PostMapping("/script-trial")
  public DataResponse<ScriptResult> scriptTrial(@Valid @RequestBody ScriptTrial body) {
    return new DataResponse<>(
        service.scriptTrial(body.companyId(), body.script(), inputValues(body.values())));
  }

  public record Simulation(WorkflowDefinition definition, @NotNull ObjectNode values) {}

  @PostMapping("/{id}/publish")
  public DataResponse<WorkflowDefinition> publish(
      @PathVariable long id, @Valid @RequestBody Version body) {
    return new DataResponse<>(service.publish(id, body.rowVersion()));
  }

  @PostMapping("/{id}/disable")
  public DataResponse<Boolean> disable(@PathVariable long id, @Valid @RequestBody Version body) {
    service.disable(id, body.rowVersion());
    return new DataResponse<>(true);
  }

  @PostMapping("/simulate")
  public DataResponse<List<WorkflowTrialStep>> simulate(@Valid @RequestBody Simulation body) {
    return new DataResponse<>(service.simulate(body.definition(), inputValues(body.values())));
  }

  /** JSON协议边界只转换真实动态变量，随后仍由目录、表达式和脚本执行器校验类型与大小。 */
  private Map<String, Object> inputValues(ObjectNode values) {
    return json.convertValue(values, new TypeReference<Map<String, Object>>() {});
  }
}
