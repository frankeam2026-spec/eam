package com.windasoft.eam.controller.equipment;

import com.windasoft.eam.dto.equipment.EquipmentHistoryView;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentNavigationNode;
import com.windasoft.eam.dto.equipment.EquipmentOptions;
import com.windasoft.eam.dto.equipment.EquipmentPage;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentQueryController {
  private final EquipmentQueryService service;

  public EquipmentQueryController(EquipmentQueryService service) {
    this.service = service;
  }

  @GetMapping
  public EquipmentPage list(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "10") long size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String company,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String serial,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String useState,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) LocalDate acquiredFrom,
      @RequestParam(required = false) LocalDate acquiredTo,
      @RequestParam(required = false) LocalDate warrantyBefore,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "desc") String direction) {
    return service.list(
        new EquipmentListQuery(
            page,
            size,
            keyword,
            category,
            state,
            department,
            company,
            model,
            serial,
            owner,
            brand,
            useState,
            source,
            acquiredFrom,
            acquiredTo,
            warrantyBefore,
            sortBy,
            direction));
  }

  @GetMapping("/{id}")
  public Map<String, EquipmentView> get(@PathVariable Long id) {
    return Map.of("data", service.get(id));
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
  public Map<String, java.util.List<EquipmentNavigationNode>> navigationTree() {
    return Map.of("data", service.navigationTree());
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
