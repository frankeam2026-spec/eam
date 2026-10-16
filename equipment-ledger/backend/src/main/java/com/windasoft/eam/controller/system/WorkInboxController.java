package com.windasoft.eam.controller.system;

import com.windasoft.eam.service.system.WorkInboxService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-inbox")
public class WorkInboxController {
  private final WorkInboxService service;

  public WorkInboxController(WorkInboxService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public Object summary() {
    return service.summary();
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "todo") String channel,
      @RequestParam(defaultValue = "pending") String state,
      @RequestParam(defaultValue = "") String module,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(channel, state, module, kind, search, page, size);
  }

  @PatchMapping("/read")
  public Object read(@RequestBody Map<String, Object> body) {
    return service.read(body);
  }
}
