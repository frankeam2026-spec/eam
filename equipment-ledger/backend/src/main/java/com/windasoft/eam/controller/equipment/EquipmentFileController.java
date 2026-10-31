package com.windasoft.eam.controller.equipment;

import com.windasoft.eam.dto.equipment.EquipmentUpload;
import com.windasoft.eam.dto.equipment.EquipmentFileUploadResult;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.service.equipment.EquipmentFileService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/equipment/files")
public class EquipmentFileController {
  private final EquipmentFileService files;

  public EquipmentFileController(EquipmentFileService files) {
    this.files = files;
  }

  @PostMapping
  public DataResponse<EquipmentFileUploadResult> upload(@RequestPart("file") MultipartFile file) throws Exception {
    try (var content = file.getInputStream()) {
      return new DataResponse<>(
          files.upload(
              new EquipmentUpload(
                  file.getOriginalFilename(), file.getSize(), file.isEmpty(), content)));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
    var file = files.download(id);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"))
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new FileSystemResource(file.path()));
  }

  @GetMapping("/{id}/preview")
  public ResponseEntity<FileSystemResource> preview(@PathVariable String id) {
    var file = files.preview(id);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "private, no-store")
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(new FileSystemResource(file.path()));
  }
}
