package com.windasoft.eam.inventory;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/queries")
public class InventoryQueryController {
  private final InventoryQueryService service;

  public InventoryQueryController(InventoryQueryService service) {
    this.service = service;
  }

  @GetMapping("/{kind}")
  public Object list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(kind, q, filters, page, size);
  }

  @GetMapping("/{kind}/export")
  public Object export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return Map.of("data", service.export(kind, q, filters));
  }
}
