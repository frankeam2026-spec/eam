package com.windasoft.eam.controller.experience;

import com.windasoft.eam.service.experience.EquipmentExperienceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentExperienceController {
  private final EquipmentExperienceService service;

  public EquipmentExperienceController(EquipmentExperienceService service) {
    this.service = service;
  }

  @GetMapping("/{id}/overview")
  public Object overview(@PathVariable long id) {
    return service.overview(id);
  }

  @GetMapping("/{id}/attention")
  public Object attention(
      @PathVariable long id,
      @RequestParam(defaultValue = "all") String kind,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.attention(id, kind, page, size);
  }

  @GetMapping("/{id}/events")
  public Object events(
      @PathVariable long id,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "asc") String sort) {
    return service.events(id, kind, page, size, sort);
  }
}
