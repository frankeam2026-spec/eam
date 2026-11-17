package com.windasoft.eam.controller.basedata;

import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.dto.basedata.BaseItemStatusForm;
import com.windasoft.eam.dto.basedata.BaseItemView;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.DeletionResult;
import com.windasoft.eam.dto.system.OrganizationView;
import com.windasoft.eam.service.basedata.BaseDataService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 基础资料和地点的协议入口；业务访问范围及引用规则由服务层核对。 */
@RestController
@RequestMapping("/api/v1/base-data")
public class BaseDataController {
  private final BaseDataService service;

  public BaseDataController(BaseDataService service) {
    this.service = service;
  }

  @GetMapping("/{resource}")
  public DataResponse<List<BaseItemView>> list(
      @PathVariable String resource, @RequestParam(required = false) Long companyId) {
    return new DataResponse<>(service.list(resource, companyId));
  }

  @PostMapping("/{resource}")
  public DataResponse<BaseItemView> create(
      @PathVariable String resource, @Valid @RequestBody BaseItemForm f) {
    return new DataResponse<>(service.create(resource, f));
  }

  @PutMapping("/{resource}/{id}")
  public DataResponse<BaseItemView> update(
      @PathVariable String resource, @PathVariable Long id, @Valid @RequestBody BaseItemForm f) {
    return new DataResponse<>(service.update(resource, id, f));
  }

  @PatchMapping("/{resource}/{id}/status")
  public DataResponse<BaseItemView> status(
      @PathVariable String resource,
      @PathVariable Long id,
      @Valid @RequestBody BaseItemStatusForm body) {
    return new DataResponse<>(service.status(resource, id, body));
  }

  @DeleteMapping("/{resource}/{id}")
  public DataResponse<DeletionResult> delete(@PathVariable String resource, @PathVariable Long id) {
    service.delete(resource, id);
    return new DataResponse<>(new DeletionResult(true));
  }

  @GetMapping("/location-companies")
  public DataResponse<List<OrganizationView>> companies() {
    return new DataResponse<>(service.companies());
  }
}
