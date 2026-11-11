package com.windasoft.eam.controller.maintenance;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.maintenance.MaintenanceFileView;
import com.windasoft.eam.dto.maintenance.MaintenanceUpload;
import com.windasoft.eam.service.maintenance.MaintenanceFileService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/maintenance/files")
public class MaintenanceFileController {
  private final MaintenanceFileService service;

  public MaintenanceFileController(MaintenanceFileService service) {
    this.service = service;
  }

  @PostMapping
  public DataResponse<MaintenanceFileView> upload(@RequestPart("file") MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      return new DataResponse<>(
          service.upload(
              new MaintenanceUpload(
                  file.getOriginalFilename(),
                  file.getContentType(),
                  file.getSize(),
                  file.isEmpty(),
                  input)));
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
                + URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20"))
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(new FileSystemResource(file.path()));
  }
}
