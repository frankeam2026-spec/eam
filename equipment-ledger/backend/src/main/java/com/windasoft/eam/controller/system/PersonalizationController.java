package com.windasoft.eam.controller.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.DeletionResult;
import com.windasoft.eam.dto.system.BrandingAssetView;
import com.windasoft.eam.dto.system.BrandingImage;
import com.windasoft.eam.dto.system.BrandingUpload;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.dto.system.PersonalizationView;
import com.windasoft.eam.dto.system.PublicBrandingView;
import com.windasoft.eam.service.system.PersonalizationService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PersonalizationController {
  private final PersonalizationService service;

  public PersonalizationController(PersonalizationService service) {
    this.service = service;
  }

  @GetMapping("/branding/public")
  public DataResponse<PublicBrandingView> publicConfig(@RequestParam long tenantId) {
    return new DataResponse<>(service.publicConfig(tenantId));
  }

  @GetMapping("/personalization")
  public DataResponse<PersonalizationView> get() {
    return new DataResponse<>(service.get());
  }

  @PutMapping("/personalization")
  public DataResponse<PersonalizationView> save(@Valid @RequestBody PersonalizationForm form)
      throws JsonProcessingException {
    return new DataResponse<>(service.save(form));
  }

  @PostMapping("/personalization/assets")
  public DataResponse<BrandingAssetView> upload(
      @RequestParam String kind, @RequestPart("file") MultipartFile file) throws IOException {
    return new DataResponse<>(
        service.upload(kind, new BrandingUpload(file.getBytes(), file.getSize(), file.isEmpty())));
  }

  @DeleteMapping("/personalization/assets/{id}")
  public DataResponse<DeletionResult> discardUpload(@PathVariable String id) {
    return new DataResponse<>(service.discardUpload(id));
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
