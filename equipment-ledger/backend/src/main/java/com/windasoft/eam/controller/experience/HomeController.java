package com.windasoft.eam.controller.experience;

import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.service.experience.HomeService;
import com.windasoft.eam.service.experience.WorkCenterService;
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
  public Object tasks(
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
  public Object home(@RequestParam(required = false) Long templateId) {
    return home.home(templateId);
  }

  @PutMapping("/home/layout")
  public Object layout(@RequestBody Map<String, Object> b) {
    return home.layout(b);
  }

  @PostMapping("/home/reset")
  public Object reset(@RequestBody Map<String, Object> b) {
    return home.reset(ExperienceSupport.id(b.get("templateId")));
  }

  @PutMapping("/home/default")
  public Object preferred(@RequestBody Map<String, Object> b) {
    return home.defaultHome(ExperienceSupport.id(b.get("templateId")));
  }

  @PostMapping("/home/recent")
  public Object recent(@RequestBody Map<String, Object> b) {
    home.access();
    return work.recent(ExperienceSupport.text(b, "url"));
  }

  @GetMapping("/home/page-preferences/{key}")
  public Object pagePreferences(@PathVariable String key) {
    return home.pagePreferences(key);
  }

  @PutMapping("/home/page-preferences/{key}")
  public Object pagePreferences(@PathVariable String key, @RequestBody Map<String, Object> values) {
    return home.pagePreferences(key, values);
  }

  @GetMapping("/home/filter-choices")
  public Object filterChoices(@RequestParam(defaultValue = "false") boolean team) {
    home.access();
    return work.filterChoices(team);
  }

  @GetMapping("/home/catalog")
  public Object catalog() {
    home.access();
    return work.catalog();
  }

  @PostMapping("/home/blocks")
  public Object blocks(@RequestBody Map<String, Object> b) {
    home.access();
    return work.block(home.normalize(Map.of("blocks", List.of(b))));
  }

  @GetMapping("/home-templates/role-choices")
  public Object roles() {
    return home.roleChoices();
  }

  @GetMapping("/home-templates")
  public Object templates() {
    return Map.of("data", home.templates());
  }

  @PostMapping("/home-templates")
  public Object create(@RequestBody Map<String, Object> b) {
    return Map.of("data", home.save(null, b));
  }

  @PutMapping("/home-templates/{id}")
  public Object save(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return Map.of("data", home.save(id, b));
  }

  @PostMapping("/home-templates/{id}/publish")
  public Object publish(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return Map.of("data", home.publish(id, b));
  }

  @PostMapping("/home-templates/{id}/disable")
  public Object disable(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return home.disable(id, b);
  }
}
