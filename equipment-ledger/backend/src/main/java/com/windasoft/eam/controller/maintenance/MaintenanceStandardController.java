package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardList;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.service.maintenance.MaintenanceStandardService;
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

/** 标准入口只绑定协议，项目及关联保存规则由标准业务Service负责。 */
@RestController
@RequestMapping("/api/v1/maintenance/standards")
public class MaintenanceStandardController {
  private final MaintenanceStandardService service;

  public MaintenanceStandardController(MaintenanceStandardService service) {
    this.service = service;
  }

  @GetMapping
  public MaintenanceStandardList list(
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam Map<String, String> parameters) {
    if (parameters.containsKey("page")) {
      var result = service.query(ListQuery.from(parameters), false);
      return new MaintenanceStandardList(result.data(), result.page());
    }
    return new MaintenanceStandardList(service.all(conditions), null);
  }

  @GetMapping("/{id:[0-9]+}")
  public DataResponse<MaintenanceStandardView> get(@PathVariable long id) {
    return new DataResponse<>(service.get(id));
  }

  @PostMapping
  public DataResponse<MaintenanceStandardView> create(
      @RequestBody MaintenanceStandardRequest request) {
    return new DataResponse<>(service.save(null, request));
  }

  @PutMapping("/{id:[0-9]+}")
  public DataResponse<MaintenanceStandardView> update(
      @PathVariable long id, @RequestBody MaintenanceStandardRequest request) {
    return new DataResponse<>(service.save(id, request));
  }

  @DeleteMapping("/{id:[0-9]+}")
  public SuccessResponse delete(@PathVariable long id) {
    service.delete(id);
    return new SuccessResponse(true);
  }
}
