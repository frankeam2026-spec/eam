package com.windasoft.eam.controller.equipment;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.equipment.EquipmentBatchForm;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.equipment.EquipmentMutationResult;
import com.windasoft.eam.dto.equipment.EquipmentRelationsForm;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.service.equipment.EquipmentCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
  private final EquipmentCommandService service;

  public EquipmentController(EquipmentCommandService service) {
    this.service = service;
  }

  @PostMapping
  public DataResponse<EquipmentView> create(@Valid @RequestBody EquipmentForm form) {
    return new DataResponse<>(service.create(form));
  }

  @PutMapping("/{id}")
  public DataResponse<EquipmentView> update(
      @PathVariable Long id, @Valid @RequestBody EquipmentForm form, @RequestParam int rowVersion) {
    return new DataResponse<>(service.update(id, form, rowVersion));
  }

  @DeleteMapping("/{id}")
  public DataResponse<EquipmentMutationResult.Deleted> delete(@PathVariable Long id) {
    return new DataResponse<>(new EquipmentMutationResult.Deleted(service.delete(id)));
  }

  @PutMapping("/{id}/details")
  public DataResponse<EquipmentView> updateDetails(
      @PathVariable Long id, @Valid @RequestBody EquipmentForm form, @RequestParam int rowVersion) {
    return new DataResponse<>(service.updateDetails(id, form, rowVersion));
  }

  @PutMapping("/{id}/children")
  public DataResponse<EquipmentView> updateChildren(
      @PathVariable Long id,
      @Valid @RequestBody EquipmentRelationsForm form,
      @RequestParam int rowVersion) {
    return new DataResponse<>(service.updateRelations(id, form.rows(), rowVersion, true));
  }

  @PutMapping("/{id}/bom")
  public DataResponse<EquipmentView> updateBom(
      @PathVariable Long id,
      @Valid @RequestBody EquipmentRelationsForm form,
      @RequestParam int rowVersion) {
    return new DataResponse<>(service.updateRelations(id, form.rows(), rowVersion, false));
  }

  @PostMapping("/batch")
  public DataResponse<EquipmentMutationResult.Count> batch(
      @Valid @RequestBody EquipmentBatchForm form) {
    return new DataResponse<>(new EquipmentMutationResult.Count(service.batch(form)));
  }
}
