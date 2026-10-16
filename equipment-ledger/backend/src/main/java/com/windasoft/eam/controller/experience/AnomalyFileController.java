package com.windasoft.eam.controller.experience;

import com.windasoft.eam.dto.experience.*;
import com.windasoft.eam.service.experience.AnomalyFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/anomalies")
public class AnomalyFileController {
  private final AnomalyFileService service;

  public AnomalyFileController(AnomalyFileService service) {
    this.service = service;
  }

  @PostMapping("/files")
  public Object upload(@RequestPart("file") MultipartFile file) throws Exception {
    try (var input = file.getInputStream()) {
      return service.upload(
          new AnomalyUpload(
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              file.isEmpty(),
              input));
    }
  }

  @GetMapping("/files/{fileId}")
  public ResponseEntity<FileSystemResource> temporary(@PathVariable String fileId) {
    return response(service.temporary(fileId));
  }

  @GetMapping("/{id}/files/{fileId}")
  public ResponseEntity<FileSystemResource> file(
      @PathVariable long id, @PathVariable String fileId) {
    return response(service.file(id, fileId));
  }

  private ResponseEntity<FileSystemResource> response(AnomalyDownload file) {
    var builder = ResponseEntity.ok().header("X-Content-Type-Options", "nosniff");
    if (file.fileName() != null)
      builder.header(
          "Content-Disposition",
          "inline; filename*=UTF-8''"
              + java.net.URLEncoder.encode(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                  .replace("+", "%20"));
    return builder
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(new FileSystemResource(file.path()));
  }
}
