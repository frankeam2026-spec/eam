package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.system.BrandingImage;
import com.windasoft.eam.dto.system.BrandingUpload;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.service.system.PersonalizationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PersonalizationController {
  private final PersonalizationService service;

  public PersonalizationController(PersonalizationService service) {
    this.service = service;
  }

  @GetMapping("/branding/public")
  public Object publicConfig(@RequestParam long tenantId) {
    return service.publicConfig(tenantId);
  }

  @GetMapping("/personalization")
  public Object get() {
    return service.get();
  }

  @PutMapping("/personalization")
  public Object save(@Valid @RequestBody PersonalizationForm form) throws Exception {
    return service.save(form);
  }

  @PostMapping("/personalization/assets")
  public Object upload(@RequestParam String kind, @RequestPart("file") MultipartFile file)
      throws Exception {
    return service.upload(
        kind, new BrandingUpload(file.getBytes(), file.getSize(), file.isEmpty()));
  }

  @DeleteMapping("/personalization/assets/{id}")
  public Object discardUpload(@PathVariable String id) {
    return service.discardUpload(id);
  }

  @GetMapping("/personalization/assets/{id}")
  public ResponseEntity<byte[]> privateAsset(@PathVariable String id) {
    return response(service.privateAsset(id));
  }

  @GetMapping("/branding/public/assets/{tenantId}/{id}")
  public ResponseEntity<byte[]> publicAsset(@PathVariable long tenantId, @PathVariable String id) {
    return response(service.publicAsset(tenantId, id));
  }

  private ResponseEntity<byte[]> response(BrandingImage image) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }
}
