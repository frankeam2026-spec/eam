package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeView;
import com.windasoft.eam.service.maintenance.MaintenanceTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 保留既有types路径；具体请求/响应不再经过多kind的Object/Map入口。 */
@RestController
@RequestMapping("/api/v1/maintenance/types")
public class MaintenanceTypeController {
  private final MaintenanceTypeService service;

  public MaintenanceTypeController(MaintenanceTypeService service) {
    this.service = service;
  }

  @GetMapping
  public DataResponse<List<MaintenanceTypeView>> list(
      @RequestParam(defaultValue = "[]") String conditions) {
    return new DataResponse<>(service.list(conditions, false));
  }

  @GetMapping("/{id}")
  public DataResponse<MaintenanceTypeView> get(@PathVariable long id) {
    return new DataResponse<>(service.get(id));
  }

  @PostMapping
  public DataResponse<MaintenanceTypeView> create(@RequestBody MaintenanceTypeRequest request) {
    return new DataResponse<>(service.save(null, request));
  }

  @PutMapping("/{id}")
  public DataResponse<MaintenanceTypeView> update(
      @PathVariable long id, @RequestBody MaintenanceTypeRequest request) {
    return new DataResponse<>(service.save(id, request));
  }

  public record Success(boolean success) {}

  @DeleteMapping("/{id}")
  public Success delete(@PathVariable long id) {
    service.delete(id);
    return new Success(true);
  }
}
