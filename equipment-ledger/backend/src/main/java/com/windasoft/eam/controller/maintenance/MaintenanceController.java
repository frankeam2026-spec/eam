package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.service.maintenance.MaintenanceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {
  private final MaintenanceService service;

  public MaintenanceController(MaintenanceService service) {
    this.service = service;
  }

  @GetMapping("/choices")
  public Object choices() {
    return service.choices();
  }

  @GetMapping("/tasks")
  public Object tasks() {
    return Map.of("data", service.tasks());
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
  public Object list(@PathVariable String kind) {
    return Map.of("data", service.definitions(kind));
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

  @GetMapping("/{kind}/export.csv")
  public ResponseEntity<String> export(
      @PathVariable String kind, @RequestParam(defaultValue = "") String keyword) {
    var data = service.exportRows(kind);
    StringBuilder csv = new StringBuilder("\uFEFF编码,名称,状态,保养日期\r\n");
    for (var row : data) {
      String name = Objects.toString(row.getOrDefault("name", row.get("equipmentName")), "");
      if (!keyword.isBlank() && !row.toString().contains(keyword)) continue;
      csv.append(cell(row.get("code")))
          .append(',')
          .append(cell(name))
          .append(',')
          .append(cell(row.get("status")))
          .append(',')
          .append(cell(row.get("dueDate")))
          .append("\r\n");
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
