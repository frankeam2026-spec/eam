package com.windasoft.eam.controller.experience;

import com.windasoft.eam.service.experience.HomeAnalyticsService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/home/analytics")
public class HomeAnalyticsController {
  private final HomeAnalyticsService service;

  public HomeAnalyticsController(HomeAnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public Object catalog() {
    return service.catalog();
  }

  @GetMapping("/presets")
  public Object presets() {
    return service.presets();
  }

  @PostMapping("/query")
  public Object query(@RequestBody Map<String, Object> body) {
    return service.query(body);
  }

  @PostMapping("/details")
  public Object details(@RequestBody Map<String, Object> body) {
    return service.details(body);
  }

  @PostMapping("/export")
  public ResponseEntity<byte[]> export(@RequestBody Map<String, Object> body) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analytics.csv")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(service.export(body));
  }
}
