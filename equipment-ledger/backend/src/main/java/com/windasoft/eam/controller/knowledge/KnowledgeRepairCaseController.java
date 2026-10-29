package com.windasoft.eam.controller.knowledge;
import com.windasoft.eam.service.knowledge.KnowledgeRepairCaseService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/knowledge/repair-cases")
public class KnowledgeRepairCaseController {
  private final KnowledgeRepairCaseService service;public KnowledgeRepairCaseController(KnowledgeRepairCaseService service){this.service=service;}
  @GetMapping("/{id}/draft") public Object draft(@PathVariable long id){return service.draft(id);}
  public record Case(long repairOrderId,long repairRowVersion,String title,String text){}
  @PostMapping public Object create(@RequestBody Case body){return service.create(body.repairOrderId(),body.repairRowVersion(),body.title(),body.text());}
}
