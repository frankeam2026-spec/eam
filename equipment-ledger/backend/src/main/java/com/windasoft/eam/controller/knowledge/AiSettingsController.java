package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.dto.knowledge.AiCallsView;
import com.windasoft.eam.dto.knowledge.AiSettingsView;
import com.windasoft.eam.service.knowledge.AiGateway;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-settings")
public class AiSettingsController {
  private final AiGateway service;

  public AiSettingsController(AiGateway service) {
    this.service = service;
  }

  @GetMapping
  public AiSettingsView settings() {
    return service.settings();
  }

  @PutMapping
  public AiSettingsView save(@Valid @RequestBody AiGateway.Settings body) {
    service.save(body);
    return service.settings();
  }

  @GetMapping("/calls")
  public AiCallsView calls(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    if (page < 1 || !Set.of(20, 50, 100).contains(size))
      throw new IllegalArgumentException("分页参数无效");
    return service.calls(page, size);
  }
}
