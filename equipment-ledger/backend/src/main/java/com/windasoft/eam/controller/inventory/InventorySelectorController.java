package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.inventory.InventorySelectorRow.Task;
import com.windasoft.eam.dto.inventory.InventorySelectorRow.User;
import com.windasoft.eam.service.inventory.InventorySelectorService;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/selectors")
public class InventorySelectorController {
  private final InventorySelectorService service;

  public InventorySelectorController(InventorySelectorService service) {
    this.service = service;
  }

  @GetMapping("/tasks")
  public PageResult<Task> tasks(
      @RequestParam Map<String, String> params,
      @RequestParam String mode,
      @RequestParam(required = false) Long warehouseId,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(required = false) Long id) {
    return service.tasks(mode, ListQuery.from(params), warehouseId, kind, id);
  }

  @GetMapping("/users")
  public PageResult<User> users(
      @RequestParam Map<String, String> params, @RequestParam long warehouseId) {
    return service.users(ListQuery.from(params), warehouseId, null);
  }

  @PostMapping("/users/resolve")
  public PageResult<User> resolveUsers(
      @RequestParam long warehouseId, @RequestBody Map<String, Object> body) {
    return service.users(
        ListQuery.from(Map.of("size", "200")),
        warehouseId,
        PlanningSelectorService.ids(body.get("ids")));
  }
}
