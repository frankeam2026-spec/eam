package com.windasoft.eam.controller.experience;

import com.windasoft.eam.service.experience.AnomalyService;
import com.windasoft.eam.service.experience.ExperienceExportService;
import com.windasoft.eam.service.experience.MaterialCustodyService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExperienceController {
  final AnomalyService anomalies;
  final MaterialCustodyService custody;
  final ExperienceExportService exports;
  final com.windasoft.eam.service.inventory.InventoryFlowService inventory;

  public ExperienceController(
      AnomalyService anomalies,
      MaterialCustodyService custody,
      ExperienceExportService exports,
      com.windasoft.eam.service.inventory.InventoryFlowService inventory) {
    this.anomalies = anomalies;
    this.custody = custody;
    this.exports = exports;
    this.inventory = inventory;
  }

  @PostMapping("/material-custody/{id}/return-request")
  public Object requestReturn(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", inventory.requestSurplusReturn(id, body));
  }

  @GetMapping("/anomalies/source")
  public Object source(@RequestParam String kind, @RequestParam long taskId) {
    return anomalies.sourceAnomalies(kind, taskId);
  }

  @GetMapping("/anomalies")
  public Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long equipmentId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String filters) {
    return anomalies.list(q, equipmentId, page, size, false, filters);
  }

  @GetMapping("/anomalies/{id}")
  public Object detail(@PathVariable long id) {
    return Map.of("data", anomalies.detail(id));
  }

  @PostMapping("/anomalies")
  public Object create(@RequestBody Map<String, Object> b) {
    return Map.of("data", anomalies.create(b));
  }

  @PostMapping("/anomalies/{id}/actions")
  public Object action(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return Map.of("data", anomalies.action(id, b));
  }

  @PostMapping("/anomalies/backfill")
  public Object backfill() {
    return anomalies.backfill();
  }

  @GetMapping("/repair-plans/owner-choices")
  public Object owners() {
    return anomalies.ownerChoices();
  }

  @GetMapping("/repair-plans")
  public Object plans(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String filters) {
    return anomalies.planList(q, page, size, filters);
  }

  @GetMapping("/repair-plans/{id}")
  public Object plan(@PathVariable long id) {
    return Map.of("data", anomalies.planDetail(id));
  }

  @PostMapping("/repair-plans")
  public Object createPlan(@RequestBody Map<String, Object> b) {
    return Map.of("data", anomalies.savePlan(null, b));
  }

  @PutMapping("/repair-plans/{id}")
  public Object savePlan(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return Map.of("data", anomalies.savePlan(id, b));
  }

  @PostMapping("/repair-plans/{id}/actions")
  public Object planAction(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return Map.of("data", anomalies.planAction(id, b));
  }

  @GetMapping("/material-custody")
  public Object materials(
      @RequestParam(defaultValue = "true") boolean personal,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String filters) {
    return custody.list(personal, q, page, size, filters);
  }

  @PutMapping("/material-custody/{id}")
  public Object custody(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return custody.save(id, b);
  }

  @GetMapping("/{kind:anomalies|repair-plans|material-custody}/export")
  public void export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String filters,
      @RequestParam(defaultValue = "true") boolean personal,
      jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    exports.authorize(kind);
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=" + kind + ".csv");
    exports.write(kind, q, filters, personal, response.getWriter());
  }
}
