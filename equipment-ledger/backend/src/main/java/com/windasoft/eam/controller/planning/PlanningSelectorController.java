package com.windasoft.eam.controller.planning;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.planning.PlanningCandidate.DefaultsRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.Equipment;
import com.windasoft.eam.dto.planning.PlanningCandidate.ResolveRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.Resolved;
import com.windasoft.eam.dto.planning.PlanningCandidate.Standard;
import com.windasoft.eam.dto.planning.PlanningCandidate.User;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/{module:maintenance|inspection}/selectors")
public class PlanningSelectorController {
  private final PlanningSelectorService service;

  public PlanningSelectorController(PlanningSelectorService service) {
    this.service = service;
  }

  @GetMapping("/equipment")
  public PageResult<Equipment> equipment(
      @PathVariable String module, @RequestParam Map<String, String> parameters) {
    return service.equipment(
        module, ListQuery.from(parameters), parameters.getOrDefault("category", ""));
  }

  @GetMapping("/users")
  public PageResult<User> users(
      @PathVariable String module, @RequestParam Map<String, String> parameters) {
    return service.users(module, ListQuery.from(parameters));
  }

  @GetMapping("/standards")
  public PageResult<Standard> standards(
      @PathVariable String module,
      @RequestParam Map<String, String> parameters,
      @RequestParam(required = false) Long equipmentId) {
    return service.standards(module, ListQuery.from(parameters), equipmentId, null);
  }

  @PostMapping("/resolve")
  public Resolved resolve(@PathVariable String module, @RequestBody ResolveRequest body) {
    return service.resolve(module, body);
  }

  @PostMapping("/defaults")
  public DataResponse<List<Standard>> defaults(
      @PathVariable String module, @RequestBody DefaultsRequest body) {
    return new DataResponse<>(service.defaults(module, body));
  }
}
