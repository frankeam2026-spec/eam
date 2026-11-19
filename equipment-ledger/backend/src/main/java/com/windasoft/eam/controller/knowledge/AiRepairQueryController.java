package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.dto.repair.RepairRecordPage;
import com.windasoft.eam.service.knowledge.AiRepairQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/assistant/repair-records")
public class AiRepairQueryController {
  private final AiRepairQueryService service;

  public AiRepairQueryController(AiRepairQueryService service) {
    this.service = service;
  }

  @GetMapping
  public RepairRecordPage query(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "") String relation,
      @RequestParam(defaultValue = "false") boolean completed,
      @RequestParam(required = false) Long equipmentId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.query(
        new AiRepairQueryService.Criteria(from, to, relation, completed, equipmentId), page, size);
  }
}
