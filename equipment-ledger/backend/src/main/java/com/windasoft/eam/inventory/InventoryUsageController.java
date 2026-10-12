package com.windasoft.eam.inventory;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/task-materials/{kind}/{id}")
public class InventoryUsageController {
  private final InventoryUsageService service;

  public InventoryUsageController(InventoryUsageService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(@PathVariable String kind, @PathVariable long id) {
    return Map.of("data", service.list(kind, id));
  }

  @PostMapping("/use")
  public Object use(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.use(kind, id, body));
  }

  @PostMapping("/remove")
  public Object remove(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.remove(kind, id, body));
  }
}
