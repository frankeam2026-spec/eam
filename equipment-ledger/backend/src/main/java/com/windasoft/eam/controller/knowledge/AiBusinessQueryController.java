package com.windasoft.eam.controller.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.service.knowledge.AiBusinessQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/assistant/business-query")
public class AiBusinessQueryController {
  private final AiBusinessQueryService service;

  public AiBusinessQueryController(AiBusinessQueryService service) {
    this.service = service;
  }

  public record Query(JsonNode plan, Long equipmentId, Integer page, Integer size) {}

  @PostMapping
  public Object query(@RequestBody Query body) {
    return service.query(
        body.plan(),
        body.equipmentId(),
        body.page() == null ? 1 : body.page(),
        body.size() == null ? 20 : body.size());
  }
}
