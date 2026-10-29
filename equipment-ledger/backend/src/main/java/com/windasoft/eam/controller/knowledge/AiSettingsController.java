package com.windasoft.eam.controller.knowledge;
import com.windasoft.eam.service.knowledge.AiGateway;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/ai-settings")
public class AiSettingsController {
  private final AiGateway service;
  public AiSettingsController(AiGateway service){this.service=service;}
  @GetMapping public Object settings(){return service.settings();}
  @PutMapping public Object save(@RequestBody AiGateway.Settings body){service.save(body);return service.settings();}
  @GetMapping("/calls") public Object calls(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){if(page<1||!java.util.Set.of(20,50,100).contains(size))throw new IllegalArgumentException("分页参数无效");return service.calls(page,size);}
}
