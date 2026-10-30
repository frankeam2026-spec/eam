package com.windasoft.eam.controller.equipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.equipment.EquipmentHistoryView;
import com.windasoft.eam.dto.equipment.EquipmentNavigationNode;
import com.windasoft.eam.dto.equipment.EquipmentOptions;
import com.windasoft.eam.dto.equipment.EquipmentPage;
import com.windasoft.eam.dto.equipment.EquipmentQueryParameters;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import java.util.Arrays;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentQueryController {
  private final EquipmentQueryService service;
  private final ObjectMapper json;

  public EquipmentQueryController(EquipmentQueryService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  @GetMapping
  public EquipmentPage list(@RequestParam Map<String, String> parameters) {
    return service.list(EquipmentQueryParameters.read(parameters, json));
  }

  @GetMapping("/export")
  public void export(
      @RequestParam Map<String, String> p, jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    var query = EquipmentQueryParameters.export(p, json);
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=equipment.csv");
    service.export(
        query,
        Arrays.asList(p.getOrDefault("columns", "code,name").split(",", -1)),
        response.getWriter());
  }

  @GetMapping("/{id}")
  public DataResponse<EquipmentView> get(@PathVariable Long id) {
    return new DataResponse<>(service.get(id));
  }

  @GetMapping(value = "/{id}/qrcode", produces = "image/png")
  public org.springframework.http.ResponseEntity<byte[]> qrCode(@PathVariable Long id)
      throws Exception {
    return org.springframework.http.ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(service.qrCode(id));
  }

  @GetMapping("/navigation-tree")
  public DataResponse<java.util.List<EquipmentNavigationNode>> navigationTree() {
    return new DataResponse<>(service.navigationTree());
  }

  @GetMapping("/owner-candidates")
  public com.windasoft.eam.dto.common.PageResult<EquipmentOptions.Employee> ownerCandidates(
      @RequestParam(defaultValue = "编辑") String action,
      @RequestParam Map<String, String> parameters) {
    return service.ownerCandidates(action, com.windasoft.eam.dto.common.ListQuery.from(parameters));
  }

  @GetMapping("/batch-options")
  public EquipmentOptions batchOptions(@RequestParam(defaultValue = "编辑") String action) {
    return service.batchOptions(action);
  }

  @GetMapping("/{id}/history")
  public EquipmentHistoryView history(@PathVariable Long id) {
    return service.history(id);
  }
}
