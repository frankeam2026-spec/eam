package com.windasoft.eam.controller.imports;

import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.imports.ImportBatchView;
import com.windasoft.eam.service.imports.EquipmentImportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/equipment/imports")
public class EquipmentImportController {
  private final EquipmentImportService service;

  public EquipmentImportController(EquipmentImportService service) {
    this.service = service;
  }

  private void download(HttpServletResponse response, String name) {
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=" + name + ".xlsx");
    response.setHeader("Cache-Control", "no-store");
  }

  @GetMapping("/template")
  public void template(HttpServletResponse response) throws IOException {
    var bytes = new ByteArrayOutputStream();
    service.template(bytes);
    download(response, "equipment-import-template");
    response.setContentLength(bytes.size());
    bytes.writeTo(response.getOutputStream());
  }

  @PostMapping(consumes = "multipart/form-data")
  public ImportBatchView upload(@RequestPart("file") MultipartFile file) throws IOException {
    return service.upload(file);
  }

  @GetMapping("/{id}")
  public ImportBatchView state(@PathVariable String id) {
    return service.state(id);
  }

  @GetMapping("/{id}/preview")
  public Object preview(
      @PathVariable String id,
      @RequestParam Map<String, String> parameters,
      @RequestParam(defaultValue = "false") boolean errorsOnly) {
    return service.preview(id, ListQuery.from(parameters), errorsOnly);
  }

  @GetMapping("/{id}/results")
  public Object results(@PathVariable String id, @RequestParam Map<String, String> parameters) {
    return service.results(id, ListQuery.from(parameters));
  }

  @GetMapping("/{id}/report")
  public void report(@PathVariable String id, HttpServletResponse response) throws IOException {
    var bytes = new ByteArrayOutputStream();
    service.report(id, bytes);
    download(response, "equipment-import-validation");
    response.setContentLength(bytes.size());
    bytes.writeTo(response.getOutputStream());
  }

  public record Confirm(@Min(0) int rowVersion, @NotBlank String idempotencyKey) {}

  @PostMapping("/{id}/confirm")
  public ImportBatchView confirm(@PathVariable String id, @RequestBody @Valid Confirm body) {
    return service.confirm(id, body.rowVersion(), body.idempotencyKey());
  }
}
