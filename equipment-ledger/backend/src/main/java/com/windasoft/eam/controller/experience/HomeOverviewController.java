package com.windasoft.eam.controller.experience;

import com.windasoft.eam.dto.experience.HomeContracts.LayoutForm;
import com.windasoft.eam.dto.experience.HomeContracts.Overview;
import com.windasoft.eam.service.experience.HomeOverviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/home/overview")
public class HomeOverviewController {
  private final HomeOverviewService overview;

  public HomeOverviewController(HomeOverviewService overview) {
    this.overview = overview;
  }

  @GetMapping
  public Overview get(@RequestParam(required = false) Long templateId) {
    return overview.get(templateId);
  }

  @PutMapping
  public Overview save(@Valid @RequestBody LayoutForm body) {
    return overview.save(body);
  }
}
