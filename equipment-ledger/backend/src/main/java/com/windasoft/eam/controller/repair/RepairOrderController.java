package com.windasoft.eam.controller.repair;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.repair.RepairAction;
import com.windasoft.eam.dto.repair.RepairFileView;
import com.windasoft.eam.dto.repair.RepairForm;
import com.windasoft.eam.dto.repair.RepairInboxView;
import com.windasoft.eam.dto.repair.RepairListResponse;
import com.windasoft.eam.dto.repair.RepairOrderView;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.dto.repair.RepairUpload;
import com.windasoft.eam.dto.repair.RepairWorkflowCommand;
import com.windasoft.eam.dto.repair.RepairWorkflowDetail;
import com.windasoft.eam.service.repair.RepairApprovalService;
import com.windasoft.eam.service.repair.RepairDetailService;
import com.windasoft.eam.service.repair.RepairFileService;
import com.windasoft.eam.service.repair.RepairOrderService;
import com.windasoft.eam.service.repair.RepairQueryService;
import com.windasoft.eam.service.repair.RepairViewAssembler;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 维修工单 HTTP 入口：负责协议转换与请求分发；状态、权限及业务校验由 Service 执行。 */
@RestController
@RequestMapping("/api/v1/repairs")
public class RepairOrderController {
  @Autowired private RepairApprovalService approvals;

  public record Resubmit(@NotNull @Min(0) Integer rowVersion) {}

  @PostMapping("/{id}/approval-submit")
  public DataResponse<Boolean> submitApproval(
      @PathVariable long id, @Valid @RequestBody Resubmit body) {
    approvals.submit(id, body.rowVersion());
    return new DataResponse<>(true);
  }

  private final RepairOrderService service;
  private final RepairFileService files;
  private final RepairWorkflowService workflow;
  private final RepairQueryService queries;
  private final RepairViewAssembler views;
  private final RepairDetailService details;

  public RepairOrderController(
      RepairOrderService service,
      RepairFileService files,
      RepairWorkflowService workflow,
      RepairQueryService queries,
      RepairViewAssembler views,
      RepairDetailService details) {
    this.details = details;
    this.service = service;
    this.files = files;
    this.workflow = workflow;
    this.queries = queries;
    this.views = views;
  }

  @PostMapping("/attachments")
  public DataResponse<RepairFileView> upload(@RequestPart("file") MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      return new DataResponse<>(
          files.upload(
              new RepairUpload(
                  file.getOriginalFilename(),
                  file.getContentType(),
                  file.getSize(),
                  file.isEmpty(),
                  input)));
    }
  }

  @GetMapping("/attachments/{id}")
  public ResponseEntity<InputStreamResource> download(@PathVariable String id) throws IOException {
    var file = files.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            file.contentType().startsWith("image/") ? "inline" : "attachment")
        .body(new InputStreamResource(Files.newInputStream(file.path())));
  }

  @GetMapping
  public RepairListResponse list(@RequestParam Map<String, String> params) {
    var result = queries.list(params);
    return new RepairListResponse(
        result.page().getRecords().stream().map(views::view).toList(),
        new PageResult.Page(
            Math.toIntExact(result.page().getCurrent()),
            Math.toIntExact(result.page().getSize()),
            result.page().getTotal()),
        result.counts());
  }

  @GetMapping("/export")
  public void export(@RequestParam Map<String, String> params, HttpServletResponse response)
      throws IOException {
    queries.requireExport();
    queries.validateExport(params);
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=repair-orders.csv");
    queries.export(params, response.getWriter());
  }

  @PostMapping
  public DataResponse<RepairOrderView> create(@Valid @RequestBody RepairForm form) {
    return new DataResponse<>(service.create(form));
  }

  @PutMapping("/{id}")
  public DataResponse<RepairOrderView> update(
      @PathVariable Long id, @Valid @RequestBody RepairForm form, @RequestParam int rowVersion) {
    return new DataResponse<>(service.update(id, form, rowVersion));
  }

  @PostMapping("/{id}/actions")
  public DataResponse<RepairOrderView> action(
      @PathVariable Long id,
      @Valid @RequestBody RepairAction request,
      @RequestParam int rowVersion) {
    return new DataResponse<>(service.action(id, request, rowVersion));
  }

  @GetMapping("/user-choices")
  public DataResponse<List<RepairReadViews.DispatchUser>> userChoices() {
    return new DataResponse<>(workflow.userChoices());
  }

  @GetMapping("/inbox")
  public DataResponse<List<RepairInboxView>> inbox() {
    return new DataResponse<>(workflow.inbox());
  }

  @PatchMapping("/inbox/{id}/read")
  public DataResponse<Boolean> readInbox(@PathVariable long id) {
    workflow.markRead(id);
    return new DataResponse<>(true);
  }

  @GetMapping("/{id}/workflow")
  public DataResponse<RepairWorkflowDetail> detail(@PathVariable long id) {
    return new DataResponse<>(details.detail(id));
  }

  /** 兼容现有按 action 区分的请求结构，具体动作字段由工作流服务校验。 */
  @PostMapping("/{id}/workflow")
  public DataResponse<RepairOrderView> workflowAction(
      @PathVariable long id,
      @RequestParam int rowVersion,
      @Valid @RequestBody RepairWorkflowCommand body) {
    return new DataResponse<>(views.view(workflow.action(id, rowVersion, body)));
  }

  @GetMapping("/{id}/stocks")
  public DataResponse<List<RepairReadViews.Stock>> stockChoices(@PathVariable long id) {
    return new DataResponse<>(workflow.stockChoices(id));
  }

  @GetMapping("/stocks")
  public DataResponse<List<RepairReadViews.Stock>> inventory() {
    return new DataResponse<>(workflow.inventory());
  }

  @PostMapping("/stocks")
  public DataResponse<Boolean> stock(@RequestBody RepairReadViews.Stock body) {
    workflow.addStock();
    return new DataResponse<>(true);
  }
}
