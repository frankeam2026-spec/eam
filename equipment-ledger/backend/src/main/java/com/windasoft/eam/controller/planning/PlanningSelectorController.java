package com.windasoft.eam.controller.planning;

import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{module:maintenance|inspection}/selectors")
public class PlanningSelectorController {
  private final PlanningSelectorService service;

  public PlanningSelectorController(PlanningSelectorService service) {
    this.service = service;
  }

  @GetMapping("/equipment")
  public Object equipment(
      @PathVariable String module, @RequestParam Map<String, String> parameters) {
    return service.equipment(
        module, ListQuery.from(parameters), parameters.getOrDefault("category", ""));
  }

  @GetMapping("/users")
  public Object users(@PathVariable String module, @RequestParam Map<String, String> parameters) {
    return service.users(module, ListQuery.from(parameters));
  }

  @GetMapping("/standards")
  public Object standards(
      @PathVariable String module,
      @RequestParam Map<String, String> parameters,
      @RequestParam(required = false) Long equipmentId) {
    return service.standards(module, ListQuery.from(parameters), equipmentId, null);
  }

  @PostMapping("/resolve")
  public Object resolve(@PathVariable String module, @RequestBody Map<String, Object> body) {
    return service.resolve(module, body);
  }

  @PostMapping("/defaults")
  public Object defaults(@PathVariable String module, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.defaults(module, body));
  }
}
