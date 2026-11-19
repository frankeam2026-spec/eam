package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.dto.knowledge.RepairAssistViews.Draft;
import com.windasoft.eam.dto.knowledge.RepairAssistViews.Transcript;
import com.windasoft.eam.service.knowledge.AiRepairService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/repair-assist")
public class AiRepairController {
  private final AiRepairService service;

  public AiRepairController(AiRepairService service) {
    this.service = service;
  }

  @PostMapping
  public Draft assist(
      @RequestParam String mode,
      @RequestParam(required = false) Long orderId,
      @RequestParam(defaultValue = "") String text,
      @RequestPart(value = "photo", required = false) MultipartFile photo)
      throws Exception {
    if (photo != null && photo.getSize() > AiRepairService.MAX_PHOTO_BYTES)
      throw new IllegalArgumentException("照片最大2MB");
    return service.assist(mode, orderId, text, photo == null ? null : photo.getBytes());
  }

  @PostMapping("/transcribe")
  public Transcript transcribe(@RequestPart("audio") MultipartFile audio) throws Exception {
    if (audio.getSize() > AiRepairService.MAX_AUDIO_BYTES)
      throw new IllegalArgumentException("录音最大3MB");
    return service.transcribe(audio.getBytes());
  }
}
