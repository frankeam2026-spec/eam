package com.windasoft.eam.controller.workflow;

import static com.windasoft.eam.dto.workflow.WorkflowResponse.exact;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.service.workflow.WorkflowTemplateService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow/templates")
public class WorkflowTemplateController {
  private final WorkflowTemplateService service;

  public WorkflowTemplateController(WorkflowTemplateService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long companyId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return exact(service.list(q, companyId, page, size));
  }

  @GetMapping("/companies")
  public Object companies(@RequestParam(defaultValue = "查看") String action) {
    return exact(Map.of("data", service.companies(action)));
  }

  @GetMapping("/variables")
  public Object variables(@RequestParam(defaultValue = "requisition") String businessType) {
    return Map.of("data", service.variables(businessType));
  }

  public record ApprovalSetting(boolean required, long rowVersion) {}

  @GetMapping("/repair-approval-setting")
  public Object approvalSetting(@RequestParam long companyId) {
    return exact(Map.of("data", service.approvalSetting(companyId)));
  }

  @PutMapping("/repair-approval-setting")
  public Object approvalSetting(@RequestParam long companyId, @RequestBody ApprovalSetting body) {
    return exact(
        Map.of("data", service.approvalSetting(companyId, body.required(), body.rowVersion())));
  }

  @GetMapping("/choices")
  public Object choices(
      @RequestParam long companyId,
      @RequestParam String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return exact(service.choices(companyId, kind, q, page, size));
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable long id, @RequestParam(required = false) Long versionId) {
    return Map.of("data", service.detail(id, versionId));
  }

  @GetMapping("/{id}/versions")
  public Object versions(@PathVariable long id) {
    return exact(Map.of("data", service.versions(id)));
  }

  @PostMapping
  public Object save(@RequestBody WorkflowDefinition body) {
    return Map.of("data", service.save(body));
  }

  public record Version(long rowVersion) {}

  public record References(long companyId, String kind, java.util.List<Long> ids) {}

  @PostMapping("/resolve")
  public Object resolve(@RequestBody References body) {
    return exact(service.resolve(body.companyId(), body.kind(), body.ids()));
  }

  public record Expression(String expression, WorkflowDefinition definition) {}

  @PostMapping("/expression")
  public Object expression(@RequestBody Expression body) {
    return exact(Map.of("data", service.parse(body.expression(), body.definition())));
  }

  public record ScriptTrial(
      long companyId, WorkflowDefinition.Script script, Map<String, Object> values) {}

  @PostMapping("/script-trial")
  public Object scriptTrial(@RequestBody ScriptTrial body) {
    return exact(
        Map.of("data", service.scriptTrial(body.companyId(), body.script(), body.values())));
  }

  public record Simulation(WorkflowDefinition definition, Map<String, Object> values) {}

  @PostMapping("/{id}/publish")
  public Object publish(@PathVariable long id, @RequestBody Version body) {
    return Map.of("data", service.publish(id, body.rowVersion()));
  }

  @PostMapping("/{id}/disable")
  public Object disable(@PathVariable long id, @RequestBody Version body) {
    service.disable(id, body.rowVersion());
    return Map.of("data", true);
  }

  @PostMapping("/simulate")
  public Object simulate(@RequestBody Simulation body) {
    return Map.of("data", service.simulate(body.definition(), body.values()));
  }
}
