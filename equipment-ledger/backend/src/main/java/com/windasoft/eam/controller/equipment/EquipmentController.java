package com.windasoft.eam.controller.equipment;

import com.windasoft.eam.dto.equipment.EquipmentBatchForm;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.service.equipment.EquipmentCommandService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
  private final EquipmentCommandService service;

  public EquipmentController(EquipmentCommandService service) {
    this.service = service;
  }

  @PostMapping
  public Map<String, EquipmentView> create(@Valid @RequestBody EquipmentForm form) {
    return Map.of("data", service.create(form));
  }

  @PutMapping("/{id}")
  public Map<String, EquipmentView> update(
      @PathVariable Long id, @Valid @RequestBody EquipmentForm form, @RequestParam int rowVersion) {
    return Map.of("data", service.update(id, form, rowVersion));
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable Long id) {
    return Map.of("data", Map.of("deleted", service.delete(id)));
  }

  @PostMapping("/batch")
  public Map<String, Object> batch(@Valid @RequestBody EquipmentBatchForm form) {
    return Map.of("data", Map.of("count", service.batch(form)));
  }
}
