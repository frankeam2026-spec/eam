package com.windasoft.eam.controller.printing;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.printing.PrintForms;
import com.windasoft.eam.dto.printing.PrintViews;
import com.windasoft.eam.service.printing.DocumentPrintService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/printing")
public class DocumentPrintController {
  private final DocumentPrintService service;

  public DocumentPrintController(DocumentPrintService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public DataResponse<PrintViews.Catalog> catalog() {
    return new DataResponse<>(service.catalog());
  }

  @GetMapping("/templates")
  public DataResponse<List<PrintViews.Template>> list() {
    return new DataResponse<>(service.templates());
  }

  @PostMapping("/templates")
  public DataResponse<PrintViews.Template> create(@Valid @RequestBody PrintForms.Template f) {
    return new DataResponse<>(service.saveTemplate(null, f));
  }

  @PutMapping("/templates/{id}")
  public DataResponse<PrintViews.Template> update(
      @PathVariable long id, @Valid @RequestBody PrintForms.Template f) {
    return new DataResponse<>(service.saveTemplate(id, f));
  }

  @DeleteMapping("/templates/{id}")
  public SuccessResponse delete(@PathVariable long id, @RequestParam int rowVersion) {
    service.deleteTemplate(id, rowVersion);
    return new SuccessResponse(true);
  }

  @PostMapping("/templates/preview")
  public ResponseEntity<byte[]> designer(@Valid @RequestBody PrintForms.Template f)
      throws IOException {
    return pdf(service.designerPreview(f));
  }

  @GetMapping("/options")
  public DataResponse<PrintViews.Options> options(@RequestParam String documentId) {
    return new DataResponse<>(service.options(documentId));
  }

  @PostMapping("/preview")
  public DataResponse<PrintViews.Preview> preview(@Valid @RequestBody PrintForms.Output f)
      throws IOException {
    return new DataResponse<>(service.preview(f));
  }

  @PostMapping("/jobs")
  public DataResponse<PrintViews.Job> generate(@Valid @RequestBody PrintForms.Output f)
      throws IOException {
    return new DataResponse<>(service.generate(f));
  }

  @GetMapping("/jobs")
  public DataResponse<List<PrintViews.Job>> jobs(@RequestParam String documentId) {
    return new DataResponse<>(service.jobs(documentId));
  }

  @GetMapping("/jobs/{id}")
  public DataResponse<PrintViews.Job> job(@PathVariable long id) {
    return new DataResponse<>(service.job(id));
  }

  @GetMapping("/jobs/{id}/file")
  public ResponseEntity<byte[]> file(@PathVariable long id) {
    return pdf(service.file(id));
  }

  private ResponseEntity<byte[]> pdf(byte[] content) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "inline; filename=outbound.pdf")
        .body(content);
  }
}
