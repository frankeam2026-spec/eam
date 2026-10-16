package com.windasoft.eam.controller.inspection;

import com.windasoft.eam.service.inspection.InspectionService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspection")
public class InspectionController {
  private final InspectionService service;

  public InspectionController(InspectionService service) {
    this.service = service;
  }

  @GetMapping("/choices")
  public Object choices() {
    return service.choices();
  }

  @GetMapping("/tasks")
  public Object tasks(@RequestParam String kind) {
    var tasks = service.tasks(kind);
    return Map.of("data", tasks, "organizations", service.taskOrganizations(kind, tasks));
  }

  @GetMapping("/tasks/{id}")
  public Object task(@PathVariable long id) {
    return Map.of("data", service.task(id));
  }

  @PostMapping("/tasks/{id}/{action}")
  public Object action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.action(id, action, body));
  }

  @PostMapping("/batch-review")
  public Object review(@RequestBody List<Map<String, Object>> body) {
    service.batchReview(body);
    return Map.of("success", true);
  }

  @PostMapping("/export")
  public Object export(@RequestBody Map<String, Object> body) {
    return Map.of(
        "data", service.exportRows(InspectionService.text(body, "kind"), body.get("ids")));
  }

  @GetMapping("/{kind}")
  public Object list(@PathVariable String kind) {
    return Map.of("data", service.definitions(kind));
  }

  @GetMapping("/{kind}/{id}")
  public Object definition(@PathVariable String kind, @PathVariable long id) {
    return Map.of("data", service.definition(kind, id));
  }

  @PostMapping("/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, null, body));
  }

  @PutMapping("/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(kind, id, body));
  }

  @DeleteMapping("/{kind}/{id}")
  public Object delete(@PathVariable String kind, @PathVariable long id) {
    service.delete(kind, id);
    return Map.of("success", true);
  }
}
