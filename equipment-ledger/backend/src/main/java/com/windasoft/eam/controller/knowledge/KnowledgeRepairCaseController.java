package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.service.knowledge.KnowledgeRepairCaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/repair-cases")
public class KnowledgeRepairCaseController {
  private final KnowledgeRepairCaseService service;

  public KnowledgeRepairCaseController(KnowledgeRepairCaseService service) {
    this.service = service;
  }

  @GetMapping("/{id}/draft")
  public KnowledgeViews.CaseDraft draft(@PathVariable long id) {
    return service.draft(id);
  }

  public record Case(long repairOrderId, long repairRowVersion, String title, String text) {}

  @PostMapping
  public KnowledgeViews.CaseCreated create(@RequestBody Case body) {
    return service.create(body.repairOrderId(), body.repairRowVersion(), body.title(), body.text());
  }
}
