package com.windasoft.eam.controller.label;

import com.windasoft.eam.dto.label.LabelPdfForm;
import com.windasoft.eam.dto.label.LabelPrintForm;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import com.windasoft.eam.service.label.LabelPrintService;
import com.windasoft.eam.service.label.LabelTemplateService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/label-templates")
public class LabelTemplateController {
  private final LabelTemplateService templates;
  private final LabelPrintService printing;

  public LabelTemplateController(LabelTemplateService templates, LabelPrintService printing) {
    this.templates = templates;
    this.printing = printing;
  }

  @GetMapping
  public Map<String, Object> list() {
    return templates.list();
  }

  @PostMapping
  public Map<String, Object> create(@Valid @RequestBody LabelTemplateForm f) {
    return templates.create(f);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable Long id, @Valid @RequestBody LabelTemplateForm f) {
    return templates.update(id, f);
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable Long id) {
    return templates.delete(id);
  }

  @PostMapping("/{id}/print-data")
  public Map<String, Object> printData(
      @PathVariable Long id, @Valid @RequestBody LabelPrintForm f) {
    return templates.printData(id, f);
  }

  @PostMapping("/{id}/pdf")
  public org.springframework.http.ResponseEntity<byte[]> pdf(
      @PathVariable Long id, @Valid @RequestBody LabelPdfForm f) throws java.io.IOException {
    return response(printing.pdf(id, f));
  }

  @PostMapping("/preview")
  public org.springframework.http.ResponseEntity<byte[]> preview(
      @Valid @RequestBody LabelTemplateForm f) throws java.io.IOException {
    return response(printing.preview(f));
  }

  private org.springframework.http.ResponseEntity<byte[]> response(byte[] data) {
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "inline; filename=equipment-labels.pdf")
        .body(data);
  }
}
