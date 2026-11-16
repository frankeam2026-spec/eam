package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceBatchAcceptance;
import com.windasoft.eam.dto.maintenance.MaintenancePostponeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskCommand;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskTarget;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskView;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.maintenance.MaintenanceTaskService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务请求按动作显式建模，协议层不承担状态判断、权限决策和批量事务。 */
@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceTaskController {
  private final MaintenanceTaskService service;
  private final MaintenanceListService lists;

  public MaintenanceTaskController(MaintenanceTaskService service, MaintenanceListService lists) {
    this.service = service;
    this.lists = lists;
  }

  @GetMapping("/tasks")
  public PageResult<MaintenanceTaskView> list(@RequestParam Map<String, String> parameters) {
    return lists.queryTasks(ListQuery.from(parameters), false);
  }

  @GetMapping("/tasks/{id:[0-9]+}")
  public DataResponse<MaintenanceTaskView> get(@PathVariable long id) {
    return new DataResponse<>(service.task(id));
  }

  @PostMapping("/tasks/{id:[0-9]+}/start")
  public DataResponse<MaintenanceTaskView> start(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Start request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/process")
  public DataResponse<MaintenanceTaskView> process(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Process request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/accept")
  public DataResponse<MaintenanceTaskView> accept(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Accept request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/suspend")
  public DataResponse<MaintenanceTaskView> suspend(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Suspend request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/resume")
  public DataResponse<MaintenanceTaskView> resume(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Resume request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/transfer")
  public DataResponse<MaintenanceTaskView> transfer(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Transfer request) {
    return new DataResponse<>(service.action(id, request));
  }

  @PostMapping("/tasks/{id:[0-9]+}/void")
  public DataResponse<MaintenanceTaskView> cancel(
      @PathVariable long id, @RequestBody MaintenanceTaskCommand.Void request) {
    return new DataResponse<>(service.action(id, request));
  }

  /** 未知动作保持旧400协议；具体动作均使用上方独立请求，兜底入口不绑定通用业务内容。 */
  @PostMapping("/tasks/{id:[0-9]+}/{action}")
  public DataResponse<MaintenanceTaskView> unknownAction(
      @PathVariable long id, @PathVariable String action) {
    throw new LocalizedArgumentException("server.inspection.message0011");
  }

  @PostMapping("/batch-delete")
  public SuccessResponse delete(@RequestBody List<MaintenanceTaskTarget> request) {
    service.batchDelete(request);
    return new SuccessResponse(true);
  }

  @PostMapping("/batch-postpone")
  public SuccessResponse postpone(@RequestBody MaintenancePostponeRequest request) {
    service.batchPostpone(request.tasks(), request.endDate(), request.reason());
    return new SuccessResponse(true);
  }

  @PostMapping("/batch-accept")
  public SuccessResponse acceptBatch(@RequestBody List<MaintenanceBatchAcceptance> request) {
    service.batchAccept(request);
    return new SuccessResponse(true);
  }
}
