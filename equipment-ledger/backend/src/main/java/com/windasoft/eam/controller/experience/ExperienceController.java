package com.windasoft.eam.controller.experience;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.experience.ExperienceDetails.Anomaly;
import com.windasoft.eam.dto.experience.ExperienceDetails.RepairPlan;
import com.windasoft.eam.dto.experience.ExperienceCommands.ChangeAnomaly;
import com.windasoft.eam.dto.experience.ExperienceCommands.ChangeRepairPlan;
import com.windasoft.eam.dto.experience.ExperienceCommands.CreateAnomaly;
import com.windasoft.eam.dto.experience.ExperienceCommands.SaveRepairPlan;
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
  public DataResponse<Anomaly> detail(@PathVariable long id) {
    return new DataResponse<>(anomalies.detail(id));
  }

  @PostMapping("/anomalies")
  public DataResponse<Anomaly> create(@RequestBody CreateAnomaly b) {
    return new DataResponse<>(anomalies.create(b));
  }

  @PostMapping("/anomalies/{id}/actions")
  public DataResponse<Anomaly> action(@PathVariable long id, @RequestBody ChangeAnomaly b) {
    return new DataResponse<>(anomalies.action(id, b));
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
  public DataResponse<RepairPlan> plan(@PathVariable long id) {
    return new DataResponse<>(anomalies.planDetail(id));
  }

  @PostMapping("/repair-plans")
  public DataResponse<RepairPlan> createPlan(@RequestBody SaveRepairPlan b) {
    return new DataResponse<>(anomalies.savePlan(null, b));
  }

  @PutMapping("/repair-plans/{id}")
  public DataResponse<RepairPlan> savePlan(@PathVariable long id, @RequestBody SaveRepairPlan b) {
    return new DataResponse<>(anomalies.savePlan(id, b));
  }

  @PostMapping("/repair-plans/{id}/actions")
  public DataResponse<RepairPlan> planAction(@PathVariable long id, @RequestBody ChangeRepairPlan b) {
    return new DataResponse<>(anomalies.planAction(id, b));
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
