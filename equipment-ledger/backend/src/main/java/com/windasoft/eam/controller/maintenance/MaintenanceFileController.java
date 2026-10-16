package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.maintenance.MaintenanceUpload;
import com.windasoft.eam.service.maintenance.MaintenanceFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/maintenance/files")
public class MaintenanceFileController {
  private final MaintenanceFileService service;

  public MaintenanceFileController(MaintenanceFileService service) {
    this.service = service;
  }

  @PostMapping
  public Object upload(@RequestPart("file") MultipartFile file) throws Exception {
    try (var input = file.getInputStream()) {
      return service.upload(
          new MaintenanceUpload(
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              file.isEmpty(),
              input));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
    var file = service.download(id);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "inline; filename*=UTF-8''"
                + java.net.URLEncoder.encode(file.name(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20"))
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(new FileSystemResource(file.path()));
  }
}
