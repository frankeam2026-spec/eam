package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.service.inventory.InventoryService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping("/choices")
  public Object choices() {
    return Map.of("data", service.choices());
  }

  @GetMapping("/masters/{kind}")
  public Object list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(kind, q, filters, page, size);
  }

  @GetMapping("/masters/{kind}/export")
  public Object export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return Map.of("data", service.export(kind, q, filters));
  }

  @PostMapping("/masters/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, null, body));
  }

  @PutMapping("/masters/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, id, body));
  }
}
