package com.windasoft.eam.controller.knowledge;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.service.knowledge.KnowledgeDocumentService;
import com.windasoft.eam.service.knowledge.KnowledgeIndexService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeDocumentController {
  private final KnowledgeDocumentService service;
  private final KnowledgeIndexService indexes;

  public KnowledgeDocumentController(
      KnowledgeDocumentService service, KnowledgeIndexService indexes) {
    this.service = service;
    this.indexes = indexes;
  }

  public record Version(
      @NotNull @Min(0) @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion) {}

  public record Applicability(
      @NotNull @Min(0) @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion,
      @NotNull Boolean all,
      @NotNull @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
          List<@NotNull Long> equipmentIds) {}

  @GetMapping("/{id}/applicability")
  public KnowledgeViews.Applicability applicability(@PathVariable long id) {
    return service.applicability(id);
  }

  @PutMapping("/{id}/applicability")
  public KnowledgeViews.RowVersion bind(
      @PathVariable long id, @Valid @RequestBody Applicability body) {
    return service.bind(id, body.rowVersion(), body.all(), body.equipmentIds());
  }

  @GetMapping("/{id}/versions/{version}/original")
  public ResponseEntity<FileSystemResource> original(
      @PathVariable long id, @PathVariable long version) {
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.APPLICATION_PDF)
        .body(new FileSystemResource(service.original(id, version)));
  }

  @GetMapping("/{id}/versions/{version}/pages/{page}/image")
  public ResponseEntity<byte[]> image(
      @PathVariable long id, @PathVariable long version, @PathVariable int page) throws Exception {
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .contentType(MediaType.IMAGE_PNG)
        .body(service.pageImage(id, version, page));
  }

  @PostMapping("/{id}/index")
  public KnowledgeViews.Status index(@PathVariable long id, @Valid @RequestBody Version body) {
    return indexes.queue(id, body.rowVersion());
  }

  @PostMapping("/{id}/publish")
  public KnowledgeViews.StatusVersion publish(
      @PathVariable long id, @Valid @RequestBody Version body) {
    return indexes.publish(id, body.rowVersion());
  }

  @PostMapping("/{id}/disable")
  public KnowledgeViews.StatusVersion disable(
      @PathVariable long id, @Valid @RequestBody Version body) {
    return service.disable(id, body.rowVersion());
  }

  @PostMapping("/{id}/versions")
  public KnowledgeViews.Replaced replace(
      @PathVariable long id, @RequestParam long rowVersion, @RequestPart("file") MultipartFile file)
      throws Exception {
    if (file.getSize() > KnowledgeDocumentService.MAX_PDF_BYTES)
      throw new IllegalArgumentException("PDF最大50MB");
    return service.replace(id, rowVersion, file.getBytes());
  }

  @GetMapping("/{id}/versions")
  public KnowledgeViews.Versions versions(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 1 || !Set.of(20, 50, 100).contains(size))
      throw new IllegalArgumentException("分页参数无效");
    return service.versions(id, page, size);
  }

  @PostMapping("/{id}/retry-parse")
  public KnowledgeViews.RowVersion retryParse(
      @PathVariable long id, @Valid @RequestBody Version body) {
    return service.retryParse(id, body.rowVersion());
  }

  @GetMapping("/companies")
  public DataResponse<List<KnowledgeViews.Company>> companies() {
    return service.companies();
  }

  @GetMapping
  public KnowledgeViews.Documents list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (q.length() > 200 || page < 1 || !Set.of(20, 50, 100).contains(size))
      throw new IllegalArgumentException("查询参数无效");
    return service.list(q, page, size);
  }

  @GetMapping("/{id}/pages")
  public DataResponse<List<KnowledgeViews.Page>> pages(@PathVariable long id) {
    return service.pages(id);
  }

  @PostMapping
  public DataResponse<KnowledgeViews.Uploaded> upload(
      @RequestParam long companyId,
      @RequestParam String title,
      @RequestParam(defaultValue = "MANUAL") String documentType,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    if (file.isEmpty() || file.getSize() > KnowledgeDocumentService.MAX_PDF_BYTES)
      throw new IllegalArgumentException("PDF文件最大50MB");
    return service.upload(companyId, title, file.getBytes(), documentType);
  }
}
