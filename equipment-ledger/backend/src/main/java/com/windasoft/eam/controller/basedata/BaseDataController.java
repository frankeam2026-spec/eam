package com.windasoft.eam.controller.basedata;

import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.service.basedata.BaseDataService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/base-data")
public class BaseDataController {
  private final BaseDataService service;

  public BaseDataController(BaseDataService service) {
    this.service = service;
  }

  @GetMapping("/{resource}")
  public Map<String, Object> list(
      @PathVariable String resource, @RequestParam(required = false) Long companyId) {
    return service.list(resource, companyId);
  }

  @PostMapping("/{resource}")
  public Map<String, Object> create(
      @PathVariable String resource, @Valid @RequestBody BaseItemForm f) {
    return service.create(resource, f);
  }

  @PutMapping("/{resource}/{id}")
  public Map<String, Object> update(
      @PathVariable String resource, @PathVariable Long id, @Valid @RequestBody BaseItemForm f) {
    return service.update(resource, id, f);
  }

  @PatchMapping("/{resource}/{id}/status")
  public Map<String, Object> status(
      @PathVariable String resource,
      @PathVariable Long id,
      @RequestBody Map<String, Integer> body) {
    return service.status(resource, id, body);
  }

  @DeleteMapping("/{resource}/{id}")
  public Map<String, Object> delete(@PathVariable String resource, @PathVariable Long id) {
    return service.delete(resource, id);
  }

  @GetMapping("/location-companies")
  public Map<String, Object> companies() {
    return service.companies();
  }
}
