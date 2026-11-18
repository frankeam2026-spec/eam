package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.system.WorkInboxViews;
import com.windasoft.eam.service.system.WorkInboxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一待办接口：展示待办视图，不作为业务单据状态的权威来源。 */
@RestController
@RequestMapping("/api/v1/work-inbox")
public class WorkInboxController {
  private final WorkInboxService service;

  public WorkInboxController(WorkInboxService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public WorkInboxViews.Summary summary() {
    return service.summary();
  }

  @GetMapping
  public WorkInboxViews.Page list(
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
  public SuccessResponse read(@RequestBody WorkInboxViews.Read body) {
    return service.read(body);
  }
}
