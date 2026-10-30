package com.windasoft.eam.controller.equipment;

import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterials;
import com.windasoft.eam.service.equipment.EquipmentRelationsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentSelectionController {
  private final EquipmentRelationsService relations;

  public EquipmentSelectionController(EquipmentRelationsService relations) {
    this.relations = relations;
  }

  @GetMapping("/child-candidates")
  public Object childCandidates(
      @RequestParam(required = false) Long parentId,
      @RequestParam(required = false) Long departmentOrgId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return relations.candidates(parentId, departmentOrgId, q, page, size);
  }

  @GetMapping("/bom-materials")
  public EquipmentBomMaterials bomMaterials(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return relations.materials(new EquipmentBomMaterialQuery(q, page, size));
  }
}
