package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceGenerationResult;
import com.windasoft.eam.dto.maintenance.MaintenancePlanDetail;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRequest;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRow;
import com.windasoft.eam.service.maintenance.MaintenanceGenerationService;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.maintenance.MaintenancePlanService;
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

/** 保持计划原详情和编辑路径；输入、响应固定类型，业务事务由计划Service负责。 */
@RestController
@RequestMapping("/api/v1/maintenance/plans")
public class MaintenancePlanController {
  private final MaintenancePlanService service;
  private final MaintenanceListService lists;
  private final MaintenanceGenerationService generation;

  public MaintenancePlanController(
      MaintenancePlanService service,
      MaintenanceListService lists,
      MaintenanceGenerationService generation) {
    this.service = service;
    this.lists = lists;
    this.generation = generation;
  }

  @GetMapping
  public PageResult<MaintenancePlanRow> list(@RequestParam Map<String, String> parameters) {
    return lists.queryPlans(ListQuery.from(parameters), false);
  }

  @GetMapping("/{id:[0-9]+}")
  public DataResponse<MaintenancePlanDetail> get(@PathVariable long id) {
    return new DataResponse<>(service.get(id));
  }

  @PostMapping
  public DataResponse<MaintenancePlanDetail> create(@RequestBody MaintenancePlanRequest request) {
    return new DataResponse<>(service.save(null, request));
  }

  @PutMapping("/{id:[0-9]+}")
  public DataResponse<MaintenancePlanDetail> update(
      @PathVariable long id, @RequestBody MaintenancePlanRequest request) {
    return new DataResponse<>(service.save(id, request));
  }

  @DeleteMapping("/{id:[0-9]+}")
  public SuccessResponse delete(@PathVariable long id) {
    service.delete(id);
    return new SuccessResponse(true);
  }

  @PostMapping("/{id}/generate")
  public MaintenanceGenerationResult generate(@PathVariable long id) {
    return new MaintenanceGenerationResult(generation.generatePlan(id));
  }
}
