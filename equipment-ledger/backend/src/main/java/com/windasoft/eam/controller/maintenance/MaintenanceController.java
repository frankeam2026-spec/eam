package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.service.maintenance.MaintenanceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessQueryService customQuery;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessConfigService customConfigs;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.maintenance.MaintenanceListService lists;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.maintenance.MaintenanceCsvService csv;

  private final MaintenanceService service;

  public MaintenanceController(MaintenanceService service) {
    this.service = service;
  }

  @GetMapping("/list-context")
  public Object listContext(@RequestParam String kind) {
    return lists.context(kind);
  }

  @GetMapping("/choices")
  public Object choices(@RequestParam(defaultValue = "false") boolean paged) {
    return service.choices(paged);
  }

  @GetMapping("/tasks")
  public Object tasks(
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam Map<String, String> parameters) {
    return lists.query("tasks", com.windasoft.eam.dto.common.ListQuery.from(parameters));
  }

  @GetMapping("/tasks/{id}")
  public Object task(@PathVariable long id) {
    return Map.of("data", service.task(id));
  }

  @PostMapping("/tasks/{id}/{action}")
  public Object action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.action(id, action, body));
  }

  @PostMapping("/batch-delete")
  public Object batchDelete(@RequestBody List<Map<String, Object>> body) {
    service.batchDelete(body);
    return Map.of("success", true);
  }

  public record PostponeRequest(List<Map<String, Object>> tasks, String endDate, String reason) {}

  @PostMapping("/batch-postpone")
  public Object batchPostpone(@RequestBody PostponeRequest body) {
    service.batchPostpone(body.tasks(), body.endDate(), body.reason());
    return Map.of("success", true);
  }

  @PostMapping("/batch-accept")
  public Object batch(@RequestBody List<Map<String, Object>> body) {
    service.batchAccept(body);
    return Map.of("success", true);
  }

  @GetMapping("/inbox")
  public Object inbox() {
    return Map.of("data", service.inbox());
  }

  @PatchMapping("/inbox/{id}/read")
  public Object read(@PathVariable long id) {
    service.read(id);
    return Map.of("success", true);
  }

  @PostMapping("/plans/{id}/generate")
  public Object generate(@PathVariable long id) {
    return Map.of("count", service.generatePlan(id));
  }

  @GetMapping("/{kind}")
  public Object list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam Map<String, String> parameters) {
    if (!kind.equals("types") && (!kind.equals("standards") || parameters.containsKey("page")))
      return lists.query(kind, com.windasoft.eam.dto.common.ListQuery.from(parameters));
    return Map.of(
        "data",
        customQuery.filter(
            kind.equals("plans") ? "maintenance_plan" : null,
            service.definitions(kind),
            conditions));
  }

  @GetMapping("/{kind}/{id}")
  public Object get(@PathVariable String kind, @PathVariable long id) {
    return Map.of("data", service.definition(kind, id));
  }

  @PostMapping("/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, null, body));
  }

  @PutMapping("/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, id, body));
  }

  @DeleteMapping("/{kind}/{id}")
  public Object delete(@PathVariable String kind, @PathVariable long id) {
    service.delete(kind, id);
    return Map.of("success", true);
  }

  @GetMapping("/{kind}/export")
  public void exportQuery(
      @PathVariable String kind,
      @RequestParam Map<String, String> parameters,
      jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    csv.write(
        kind,
        com.windasoft.eam.dto.common.ListQuery.from(parameters),
        parameters.get("columns"),
        () -> {
          response.setContentType("text/csv;charset=UTF-8");
          response.setHeader("Content-Disposition", "attachment; filename=maintenance.csv");
          try {
            return response.getWriter();
          } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
  }

  @GetMapping("/{kind}/export.csv")
  public ResponseEntity<String> export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "[]") String conditions) {
    if (!kind.equals("types"))
      return ResponseEntity.status(307)
          .location(
              java.net.URI.create(
                  "/api/v1/maintenance/"
                      + kind
                      + "/export?keyword="
                      + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                      + "&conditions="
                      + java.net.URLEncoder.encode(
                          conditions, java.nio.charset.StandardCharsets.UTF_8)))
          .build();
    String object =
        kind.equals("tasks")
            ? "maintenance_task"
            : kind.equals("plans") ? "maintenance_plan" : null;
    var data = customQuery.filter(object, service.exportRows(kind), conditions);
    var fields =
        object == null
            ? java.util.List.<com.fasterxml.jackson.databind.JsonNode>of()
            : customConfigs
                .fields(customConfigs.decode(customConfigs.current(object, false).get("published")))
                .values()
                .stream()
                .filter(f -> f.path("exportable").asBoolean())
                .toList();
    StringBuilder csv = new StringBuilder("\uFEFF编码,名称,状态,保养日期");
    for (var field : fields) csv.append(',').append(cell(field.path("name").asText()));
    csv.append("\r\n");
    for (var row : data) {
      String name = Objects.toString(row.getOrDefault("name", row.get("equipmentName")), "");
      if (!keyword.isBlank() && !row.toString().contains(keyword)) continue;
      csv.append(cell(row.get("code")))
          .append(',')
          .append(cell(name))
          .append(',')
          .append(cell(row.get("status")))
          .append(',')
          .append(cell(row.get("dueDate")));
      var labels = row.get("customFieldLabels") instanceof Map<?, ?> values ? values : Map.of();
      for (var field : fields) csv.append(',').append(cell(labels.get(field.path("id").asText())));
      csv.append("\r\n");
    }
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=maintenance.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv.toString());
  }

  private String cell(Object value) {
    String v = Objects.toString(value, "");
    if (v.matches("^[=+@-].*")) v = "'" + v;
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}
