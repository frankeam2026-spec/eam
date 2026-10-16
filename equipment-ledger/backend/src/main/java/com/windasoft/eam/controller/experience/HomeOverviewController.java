package com.windasoft.eam.controller.experience;

import com.windasoft.eam.service.experience.HomeOverviewService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/home/overview")
public class HomeOverviewController {
  private final HomeOverviewService overview;

  public HomeOverviewController(HomeOverviewService overview) {
    this.overview = overview;
  }

  @GetMapping
  public Object get(@RequestParam(required = false) Long templateId) {
    return overview.get(templateId);
  }

  @PutMapping
  public Object save(@RequestBody Map<String, Object> body) {
    return overview.save(body);
  }
}
