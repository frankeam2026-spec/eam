package com.windasoft.eam.controller.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.repair.RepairAction;
import com.windasoft.eam.dto.repair.RepairForm;
import com.windasoft.eam.dto.repair.RepairUpload;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.service.repair.RepairFileService;
import com.windasoft.eam.service.repair.RepairOrderService;
import com.windasoft.eam.service.repair.RepairQueryService;
import com.windasoft.eam.service.repair.RepairViewAssembler;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/repairs")
public class RepairOrderController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.repair.RepairApprovalService approvals;

  public record Resubmit(int rowVersion) {}

  @PostMapping("/{id}/approval-submit")
  public Object submitApproval(@PathVariable long id, @RequestBody Resubmit body) {
    approvals.submit(id, body.rowVersion());
    return Map.of("data", true);
  }

  private final RepairOrderService service;
  private final RepairFileService files;
  private final RepairWorkflowService workflow;
  private final RepairQueryService queries;
  private final RepairViewAssembler views;

  public RepairOrderController(
      RepairOrderService service,
      RepairFileService files,
      RepairWorkflowService workflow,
      RepairQueryService queries,
      RepairViewAssembler views) {
    this.service = service;
    this.files = files;
    this.workflow = workflow;
    this.queries = queries;
    this.views = views;
  }

  @PostMapping("/attachments")
  public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
    try (var input = file.getInputStream()) {
      return files.upload(
          new RepairUpload(
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              file.isEmpty(),
              input));
    }
  }

  @GetMapping("/attachments/{id}")
  public ResponseEntity<InputStreamResource> download(@PathVariable String id) throws IOException {
    var file = files.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            file.contentType().startsWith("image/") ? "inline" : "attachment")
        .body(new InputStreamResource(Files.newInputStream(file.path())));
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam Map<String, String> params) {
    var result = queries.list(params);
    return Map.of(
        "data",
        result.page().getRecords().stream().map(views::view).toList(),
        "page",
        Map.of(
            "number",
            result.page().getCurrent(),
            "size",
            result.page().getSize(),
            "total",
            result.page().getTotal()),
        "counts",
        result.counts());
  }

  @GetMapping("/export")
  public void export(
      @RequestParam Map<String, String> params, jakarta.servlet.http.HttpServletResponse response)
      throws IOException {
    queries.requireExport();
    queries.validateExport(params);
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=repair-orders.csv");
    queries.export(params, response.getWriter());
  }

  @PostMapping
  public Map<String, Object> create(@Valid @RequestBody RepairForm form) {
    return service.create(form);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable Long id, @Valid @RequestBody RepairForm form, @RequestParam int rowVersion) {
    return service.update(id, form, rowVersion);
  }

  @PostMapping("/{id}/actions")
  public Map<String, Object> action(
      @PathVariable Long id,
      @Valid @RequestBody RepairAction request,
      @RequestParam int rowVersion) {
    return service.action(id, request, rowVersion);
  }

  @GetMapping("/user-choices")
  public Map<String, Object> userChoices() {
    return Map.of("data", workflow.userChoices());
  }

  @GetMapping("/inbox")
  public Map<String, Object> inbox() {
    return Map.of("data", workflow.inbox());
  }

  @PatchMapping("/inbox/{id}/read")
  public Map<String, Object> readInbox(@PathVariable long id) {
    workflow.markRead(id);
    return Map.of("data", true);
  }

  @GetMapping("/{id}/workflow")
  public Map<String, Object> detail(@PathVariable long id) {
    var result = new java.util.LinkedHashMap<>(workflow.detail(id));
    result.put("order", views.view((RepairOrder) result.get("order")));
    return Map.of("data", result);
  }

  @PostMapping("/{id}/workflow")
  public Map<String, Object> workflowAction(
      @PathVariable long id, @RequestParam int rowVersion, @RequestBody JsonNode body) {
    return Map.of("data", views.view(workflow.action(id, rowVersion, body)));
  }

  @GetMapping("/{id}/stocks")
  public Map<String, Object> stockChoices(@PathVariable long id) {
    return Map.of("data", workflow.stockChoices(id));
  }

  @GetMapping("/stocks")
  public Map<String, Object> inventory() {
    return Map.of("data", workflow.inventory());
  }

  @PostMapping("/stocks")
  public Map<String, Object> stock(@RequestBody JsonNode body) {
    workflow.addStock(body);
    return Map.of("data", true);
  }
}
