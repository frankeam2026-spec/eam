package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.service.inventory.OutboundService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/outbounds")
public class OutboundController {
  private final OutboundService service;

  public OutboundController(OutboundService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(q, filters, conditions, page, size);
  }

  @GetMapping("/materials")
  public Object materials(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam long warehouseId) {
    return service.materials(q, page, size, warehouseId);
  }

  @GetMapping("/choices")
  public Object choices() {
    return Map.of("data", service.choices());
  }

  @GetMapping("/export")
  public Object export(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "[]") String conditions) {
    return Map.of("data", service.export(q, filters, conditions));
  }

  @GetMapping("/stock")
  public Object stock(
      @RequestParam long warehouseId,
      @RequestParam long materialId,
      @RequestParam(defaultValue = "0") long requisitionLineId) {
    return Map.of("data", service.stock(warehouseId, materialId, requisitionLineId));
  }

  @PostMapping("/from-requests")
  public Object fromRequests(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.fromRequests(body));
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable String id) {
    return Map.of("data", service.detail(id));
  }

  @PostMapping
  public Object create(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(null, body));
  }

  @PutMapping("/{id}")
  public Object update(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(id, body));
  }

  @PostMapping("/{id}/preview")
  public Object preview(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.preview(id, body));
  }

  @PostMapping("/{id}/confirm")
  public Object confirm(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.confirm(id, body));
  }

  @DeleteMapping("/{id}")
  public Object delete(@PathVariable long id, @RequestBody Map<String, Object> body) {
    service.delete(id, body);
    return Map.of("data", true);
  }

  @GetMapping("/pending")
  public Object pending(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.pending(q, filters, conditions, page, size, false);
  }

  @GetMapping("/pending-export")
  public Object pendingExport(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "[]") String conditions) {
    return Map.of("data", service.pending(q, filters, conditions, 1, 100, true));
  }
}
