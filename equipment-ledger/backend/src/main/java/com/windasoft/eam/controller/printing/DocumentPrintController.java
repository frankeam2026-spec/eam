package com.windasoft.eam.controller.printing;

import com.windasoft.eam.dto.printing.PrintForms;
import com.windasoft.eam.service.printing.DocumentPrintService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/printing")
public class DocumentPrintController {
  private final DocumentPrintService service;

  public DocumentPrintController(DocumentPrintService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public Object catalog() {
    return Map.of("data", service.catalog());
  }

  @GetMapping("/templates")
  public Object list() {
    return Map.of("data", service.templates());
  }

  @PostMapping("/templates")
  public Object create(@Valid @RequestBody PrintForms.Template f) {
    return Map.of("data", service.saveTemplate(null, f));
  }

  @PutMapping("/templates/{id}")
  public Object update(@PathVariable long id, @Valid @RequestBody PrintForms.Template f) {
    return Map.of("data", service.saveTemplate(id, f));
  }

  @DeleteMapping("/templates/{id}")
  public Object delete(@PathVariable long id, @RequestParam int rowVersion) {
    service.deleteTemplate(id, rowVersion);
    return Map.of("success", true);
  }

  @PostMapping("/templates/preview")
  public ResponseEntity<byte[]> designer(@Valid @RequestBody PrintForms.Template f)
      throws java.io.IOException {
    return pdf(service.designerPreview(f));
  }

  @GetMapping("/options")
  public Object options(@RequestParam String documentId) {
    return Map.of("data", service.options(documentId));
  }

  @PostMapping("/preview")
  public Object preview(@Valid @RequestBody PrintForms.Output f) throws java.io.IOException {
    return Map.of("data", service.preview(f));
  }

  @PostMapping("/jobs")
  public Object generate(@Valid @RequestBody PrintForms.Output f) throws java.io.IOException {
    return Map.of("data", service.generate(f));
  }

  @GetMapping("/jobs")
  public Object jobs(@RequestParam String documentId) {
    return Map.of("data", service.jobs(documentId));
  }

  @GetMapping("/jobs/{id}")
  public Object job(@PathVariable long id) {
    return Map.of("data", service.job(id));
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
