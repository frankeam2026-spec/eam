package com.windasoft.eam.controller.inspection;

import com.windasoft.eam.dto.inspection.InspectionUpload;
import com.windasoft.eam.service.inspection.InspectionFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspection/files")
public class InspectionFileController {
  private final InspectionFileService service;

  public InspectionFileController(InspectionFileService service) {
    this.service = service;
  }

  @PostMapping
  public Object upload(@RequestPart("file") MultipartFile file) throws Exception {
    try (var input = file.getInputStream()) {
      return service.upload(
          new InspectionUpload(
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
