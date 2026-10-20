package com.windasoft.eam.controller.inspection;

import com.windasoft.eam.service.inspection.InspectionService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspection")
public class InspectionController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessQueryService customQuery;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.inspection.InspectionListService lists;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.inspection.InspectionCsvService csv;

  private final InspectionService service;

  public InspectionController(InspectionService service) {
    this.service = service;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.inspection.DailyArrangementService arrangements;

  @GetMapping("/daily-event-options")
  public Object eventOptions(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.dailyEventOptions(q, page, size);
  }

  @PostMapping("/daily-plans/preview")
  public Object dailyPreview(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.dailyPreview(body));
  }

  @PostMapping("/daily-plans/{id}/events")
  public Object dailyEvent(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.dailyEvent(id, body));
  }

  @PostMapping("/daily-plans/{id}/exemptions")
  public Object dailyExemption(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.dailyExemption(id, body));
  }

  @GetMapping("/daily-operation-teams")
  public Object teams() {
    return Map.of("data", arrangements.teams());
  }

  @PostMapping("/daily-operation-teams")
  public Object createTeam(@RequestBody Map<String, Object> body) {
    return Map.of("data", arrangements.createTeam(body));
  }

  @PutMapping("/daily-operation-teams/{id}")
  public Object updateTeam(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", arrangements.updateTeam(id, body));
  }

  @GetMapping("/list-context")
  public Object context(@RequestParam String kind) {
    return lists.context(kind);
  }

  @GetMapping("/{kind}/export")
  public void exportCsv(
      @PathVariable String kind,
      @RequestParam Map<String, String> parameters,
      jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    csv.write(
        kind,
        com.windasoft.eam.dto.common.ListQuery.from(parameters),
        parameters.get("columns"),
        () -> {
          response.setContentType("text/csv;charset=UTF-8");
          response.setHeader("Content-Disposition", "attachment; filename=inspection.csv");
          try {
            return response.getWriter();
          } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
  }

  @GetMapping("/choices")
  public Object choices(@RequestParam(defaultValue = "false") boolean paged) {
    return service.choices(paged);
  }

  @GetMapping("/tasks")
  public Object tasks(
      @RequestParam String kind,
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam Map<String, String> parameters) {
    return lists.query(kind + "-tasks", com.windasoft.eam.dto.common.ListQuery.from(parameters));
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
        "data",
        service.exportRows(
            com.windasoft.eam.common.inspection.InspectionSupport.text(body, "kind"),
            body.get("ids")));
  }

  @GetMapping("/{kind}")
  public Object list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "[]") String conditions,
      @RequestParam Map<String, String> parameters) {
    if (!kind.equals("types") && (!kind.equals("standards") || parameters.containsKey("page")))
      return lists.query(kind, com.windasoft.eam.dto.common.ListQuery.from(parameters));
    return Map.of(
        "data",
        customQuery.filter(
            com.windasoft.eam.service.business.BusinessObjects.inspection(kind),
            service.definitions(kind),
            conditions));
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
