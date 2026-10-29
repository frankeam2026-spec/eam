package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.service.knowledge.AiRepairQueryService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/assistant/repair-records")
public class AiRepairQueryController {
  private final AiRepairQueryService service;
  public AiRepairQueryController(AiRepairQueryService service){this.service=service;}
  @GetMapping public Object query(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="")String relation,@RequestParam(defaultValue="false")boolean completed,@RequestParam(required=false)Long equipmentId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size) throws Exception {
    return service.query(new AiRepairQueryService.Criteria(from,to,relation,completed,equipmentId),page,size);
  }
}
