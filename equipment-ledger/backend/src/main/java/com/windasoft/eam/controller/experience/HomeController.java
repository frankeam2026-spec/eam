package com.windasoft.eam.controller.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.experience.WorkTaskPage;
import com.windasoft.eam.dto.experience.WorkTaskPage.ScopeChoices;
import com.windasoft.eam.dto.experience.HomeContracts.Identity;
import com.windasoft.eam.dto.experience.HomeContracts.LayoutForm;
import com.windasoft.eam.dto.experience.HomeContracts.Recent;
import com.windasoft.eam.dto.experience.HomeContracts.RoleChoice;
import com.windasoft.eam.dto.experience.HomeContracts.State;
import com.windasoft.eam.dto.experience.HomeContracts.TemplateForm;
import com.windasoft.eam.dto.experience.HomeContracts.TemplateView;
import com.windasoft.eam.dto.experience.HomeContracts.Version;
import com.windasoft.eam.service.experience.HomeService;
import com.windasoft.eam.service.experience.WorkCenterService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HomeController {
  final HomeService home;
  final WorkCenterService work;

  public HomeController(HomeService home, WorkCenterService work) {
    this.home = home;
    this.work = work;
  }

  @GetMapping("/work-center/tasks")
  public WorkTaskPage tasks(
      @RequestParam String state,
      @RequestParam(defaultValue = "") String module,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String companyId,
      @RequestParam(defaultValue = "") String departmentId) {
    home.access();
    return work.queue(state, module, search, page, size, status, companyId, departmentId);
  }

  @GetMapping("/home")
  public State home(@RequestParam(required = false) Long templateId) {
    return home.home(templateId);
  }

  @PutMapping("/home/layout")
  public State layout(@Valid @RequestBody LayoutForm b) {
    return home.layout(b);
  }

  @PostMapping("/home/reset")
  public State reset(@Valid @RequestBody Identity b) {
    return home.reset(b.templateId());
  }

  @PutMapping("/home/default")
  public SuccessResponse preferred(@Valid @RequestBody Identity b) {
    return home.defaultHome(b.templateId());
  }

  @PostMapping("/home/recent")
  public SuccessResponse recent(@RequestBody Recent b) {
    home.access();
    return work.recent(Objects.toString(b.url(), "").trim());
  }

  @GetMapping("/home/page-preferences/{key}")
  public DataResponse<JsonNode> pagePreferences(@PathVariable String key) {
    return new DataResponse<>(home.pagePreferences(key));
  }

  @PutMapping("/home/page-preferences/{key}")
  public SuccessResponse pagePreferences(@PathVariable String key, @RequestBody JsonNode values) {
    return home.pagePreferences(key, values);
  }

  @GetMapping("/home/filter-choices")
  public ScopeChoices filterChoices(@RequestParam(defaultValue = "false") boolean team) {
    home.access();
    return work.filterChoices(team);
  }

  @GetMapping("/home/catalog")
  public DataResponse<List<String>> catalog() {
    home.access();
    return new DataResponse<>(work.catalog());
  }

  @PostMapping("/home/blocks")
  public Object blocks(@RequestBody Map<String, Object> b) {
    home.access();
    return work.block(home.normalize(Map.of("blocks", List.of(b))));
  }

  @GetMapping("/home-templates/role-choices")
  public DataResponse<List<RoleChoice>> roles() {
    return new DataResponse<>(home.roleChoices());
  }

  @GetMapping("/home-templates")
  public DataResponse<List<TemplateView>> templates() {
    return new DataResponse<>(home.templates());
  }

  @PostMapping("/home-templates")
  public DataResponse<TemplateView> create(@RequestBody TemplateForm b) {
    return new DataResponse<>(home.save(null, b));
  }

  @PutMapping("/home-templates/{id}")
  public DataResponse<TemplateView> save(@PathVariable long id, @RequestBody TemplateForm b) {
    return new DataResponse<>(home.save(id, b));
  }

  @PostMapping("/home-templates/{id}/publish")
  public DataResponse<TemplateView> publish(@PathVariable long id, @Valid @RequestBody Version b) {
    return new DataResponse<>(home.publish(id, b));
  }

  @PostMapping("/home-templates/{id}/disable")
  public SuccessResponse disable(@PathVariable long id, @Valid @RequestBody Version b) {
    return home.disable(id, b);
  }
}
