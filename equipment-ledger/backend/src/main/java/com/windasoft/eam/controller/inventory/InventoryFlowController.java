package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.service.inventory.InventoryFlowService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryFlowController {
  private final InventoryFlowService service;

  public InventoryFlowController(InventoryFlowService service) {
    this.service = service;
  }

  @GetMapping("/operations/{kind}")
  public Object list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(kind, q, filters, page, size, false);
  }

  @GetMapping("/operations/{kind}/export")
  public Object export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return service.list(kind, q, filters, 1, 100, true);
  }

  @GetMapping("/operations/{kind}/choices")
  public Object choices(@PathVariable String kind) {
    return Map.of("data", service.choices(kind));
  }

  @GetMapping("/operations/{kind}/{id}")
  public Object detail(@PathVariable String kind, @PathVariable long id) {
    return Map.of("data", service.detail(kind, id));
  }

  @PostMapping("/operations/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, null, body));
  }

  @PutMapping("/operations/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, id, body));
  }

  @PostMapping("/operations/{kind}/{id}/confirm")
  public Object confirm(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.confirm(kind, id, body, false));
  }

  @PostMapping("/operations/{kind}/{id}/receive")
  public Object receive(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.confirm(kind, id, body, true));
  }

  @DeleteMapping("/operations/{kind}/{id}")
  public Object delete(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    service.delete(kind, id, body);
    return Map.of("data", true);
  }

  @PostMapping("/balances/{id}/state")
  public Object freeze(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.freeze(id, body));
  }
}
