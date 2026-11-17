package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceChoices;
import com.windasoft.eam.dto.maintenance.MaintenanceInboxView;
import com.windasoft.eam.dto.maintenance.MaintenanceListContext;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.maintenance.MaintenanceCsvService;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公共选项、待办与兼容CSV入口；类型、标准、计划、任务CRUD由各自的明确协议入口提供。 */
@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {
  private final BusinessQueryService customQuery;
  private final MaintenanceListService lists;
  private final MaintenanceCsvService csv;
  private final MaintenanceService service;

  public MaintenanceController(
      MaintenanceService service,
      MaintenanceListService lists,
      MaintenanceCsvService csv,
      BusinessQueryService customQuery) {
    this.service = service;
    this.lists = lists;
    this.csv = csv;
    this.customQuery = customQuery;
  }

  @GetMapping("/list-context")
  public MaintenanceListContext listContext(@RequestParam String kind) {
    return lists.context(kind);
  }

  @GetMapping("/choices")
  public MaintenanceChoices choices(@RequestParam(defaultValue = "false") boolean paged) {
    return service.choices(paged);
  }

  @GetMapping("/inbox")
  public DataResponse<List<MaintenanceInboxView>> inbox() {
    return new DataResponse<>(service.inbox());
  }

  @PatchMapping("/inbox/{id}/read")
  public SuccessResponse read(@PathVariable long id) {
    service.read(id);
    return new SuccessResponse(true);
  }

  @GetMapping("/{kind}/export")
  public void exportQuery(
      @PathVariable String kind,
      @RequestParam Map<String, String> parameters,
      HttpServletResponse response)
      throws IOException {
    csv.write(
        kind,
        ListQuery.from(parameters),
        parameters.get("columns"),
        () -> {
          response.setContentType("text/csv;charset=UTF-8");
          response.setHeader("Content-Disposition", "attachment; filename=maintenance.csv");
          try {
            return response.getWriter();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /** 旧.csv地址保留307；只有类型沿用旧四列CSV，不恢复已经替代的动态JSON写入入口。 */
  @GetMapping("/{kind}/export.csv")
  public ResponseEntity<String> export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "[]") String conditions) {
    if (!kind.equals("types")) {
      return ResponseEntity.status(307)
          .location(
              URI.create(
                  "/api/v1/maintenance/"
                      + kind
                      + "/export?keyword="
                      + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                      + "&conditions="
                      + URLEncoder.encode(conditions, StandardCharsets.UTF_8)))
          .build();
    }
    var data = customQuery.filter(null, service.exportRows(kind), conditions);
    StringBuilder output = new StringBuilder("﻿编码,名称,状态,保养日期");
    output.append("\r\n");
    for (var row : data) {
      String name = Objects.toString(row.getOrDefault("name", row.get("equipmentName")), "");
      if (!keyword.isBlank() && !row.toString().contains(keyword)) {
        continue;
      }
      output
          .append(cell(row.get("code")))
          .append(',')
          .append(cell(name))
          .append(',')
          .append(cell(row.get("status")))
          .append(',')
          .append(cell(row.get("dueDate")))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=maintenance.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(output.toString());
  }

  private String cell(Object value) {
    String text = Objects.toString(value, "");
    if (text.matches("^[=+@-].*")) {
      text = "'" + text;
    }
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
