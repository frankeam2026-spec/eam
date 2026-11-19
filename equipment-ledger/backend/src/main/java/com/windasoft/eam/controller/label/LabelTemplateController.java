package com.windasoft.eam.controller.label;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.label.LabelPdfForm;
import com.windasoft.eam.dto.label.LabelPrintForm;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import com.windasoft.eam.dto.label.LabelTemplateView;
import com.windasoft.eam.dto.label.LabelTemplateView.PrintData;
import com.windasoft.eam.service.label.LabelPrintService;
import com.windasoft.eam.service.label.LabelTemplateService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
  public DataResponse<List<LabelTemplateView>> list() {
    return new DataResponse<>(templates.list());
  }

  @PostMapping
  public DataResponse<LabelTemplateView> create(@Valid @RequestBody LabelTemplateForm f) {
    return new DataResponse<>(templates.create(f));
  }

  @PutMapping("/{id}")
  public DataResponse<LabelTemplateView> update(
      @PathVariable Long id, @Valid @RequestBody LabelTemplateForm f) {
    return new DataResponse<>(templates.update(id, f));
  }

  @DeleteMapping("/{id}")
  public DataResponse<Boolean> delete(@PathVariable Long id) {
    templates.delete(id);
    return new DataResponse<>(true);
  }

  @PostMapping("/{id}/print-data")
  public PrintData printData(@PathVariable Long id, @Valid @RequestBody LabelPrintForm f) {
    return templates.printData(id, f);
  }

  @PostMapping("/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable Long id, @Valid @RequestBody LabelPdfForm f)
      throws IOException {
    return response(printing.pdf(id, f));
  }

  @PostMapping("/preview")
  public ResponseEntity<byte[]> preview(@Valid @RequestBody LabelTemplateForm f)
      throws IOException {
    return response(printing.preview(f));
  }

  private ResponseEntity<byte[]> response(byte[] data) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "inline; filename=equipment-labels.pdf")
        .body(data);
  }
}
