package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.inventory.RequisitionDetail;
import com.windasoft.eam.dto.inventory.RequisitionDraftForm;
import com.windasoft.eam.service.inventory.RequisitionService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/requisitions")
public class RequisitionController {
  private final RequisitionService service;

  public RequisitionController(RequisitionService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(q, filters, page, size);
  }

  @GetMapping("/export")
  public Object export(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return Map.of("data", service.export(q, filters));
  }

  @GetMapping("/choices")
  public Object choices(
      @RequestParam(defaultValue = "false") boolean paged,
      @RequestParam(defaultValue = "") String taskKind,
      @RequestParam(required = false) Long taskId) {
    return Map.of("data", service.choices(paged, taskKind, taskId));
  }

  @GetMapping("/materials")
  public Object materials(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam String taskKind,
      @RequestParam long taskId,
      @RequestParam long warehouseId) {
    return service.materials(q, page, size, taskKind, taskId, warehouseId);
  }

  @GetMapping("/{id}")
  public DataResponse<RequisitionDetail> detail(@PathVariable long id) {
    return new DataResponse<>(service.detailView(id));
  }

  @PostMapping
  public DataResponse<RequisitionDetail> create(@RequestBody RequisitionDraftForm body) {
    return new DataResponse<>(service.create(body));
  }

  @PutMapping("/{id}")
  public DataResponse<RequisitionDetail> update(
      @PathVariable long id, @RequestBody RequisitionDraftForm body) {
    return new DataResponse<>(service.update(id, body));
  }

  @PostMapping("/{id}/submit")
  public Object submit(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.submit(id, body));
  }

  @PostMapping("/merged-preview")
  public Object mergedPreview(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.mergedPreview(body));
  }

  @PostMapping("/merged-confirm")
  public Object mergedConfirm(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.mergedConfirm(body));
  }

  @GetMapping("/{id}/stock-pool")
  public Object stockPool(@PathVariable long id) {
    return Map.of("data", service.stockPool(id));
  }

  @PostMapping("/{id}/reserve")
  public Object reserve(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.reserve(id, body));
  }

  @PostMapping("/{id}/release")
  public Object release(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.release(id, body));
  }

  @PostMapping("/{id}/cancel")
  public Object cancel(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.cancel(id, body));
  }

  @PostMapping("/{id}/issue-preview")
  public Object previewIssue(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.previewIssue(id, body));
  }

  @PostMapping("/{id}/issue-confirm")
  public Object confirmIssue(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.confirmIssue(id, body));
  }

  @DeleteMapping("/{id}")
  public Object delete(@PathVariable long id, @RequestBody Map<String, Object> body) {
    service.delete(id, body);
    return Map.of("data", true);
  }

  @PostMapping("/{id}/close-remaining")
  public Object closeRemaining(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.closeRemaining(id, body));
  }
}
