package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.service.inventory.InventorySelectorService;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/selectors")
public class InventorySelectorController {
  private final InventorySelectorService service;

  public InventorySelectorController(InventorySelectorService service) {
    this.service = service;
  }

  @GetMapping("/tasks")
  public Object tasks(
      @RequestParam Map<String, String> params,
      @RequestParam String mode,
      @RequestParam(required = false) Long warehouseId,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(required = false) Long id) {
    return service.tasks(mode, ListQuery.from(params), warehouseId, kind, id);
  }

  @GetMapping("/users")
  public Object users(@RequestParam Map<String, String> params, @RequestParam long warehouseId) {
    return service.users(ListQuery.from(params), warehouseId, null);
  }

  @PostMapping("/users/resolve")
  public Object resolveUsers(
      @RequestParam long warehouseId, @RequestBody Map<String, Object> body) {
    return service.users(
        ListQuery.from(Map.of("size", "200")),
        warehouseId,
        PlanningSelectorService.ids(body.get("ids")));
  }
}
