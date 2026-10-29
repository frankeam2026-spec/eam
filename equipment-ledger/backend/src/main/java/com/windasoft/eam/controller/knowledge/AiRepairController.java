package com.windasoft.eam.controller.knowledge;
import com.windasoft.eam.service.knowledge.AiRepairService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1/repair-assist")
public class AiRepairController {
  private final AiRepairService service;public AiRepairController(AiRepairService service){this.service=service;}
  @PostMapping public Object assist(@RequestParam String mode,@RequestParam(required=false)Long orderId,@RequestParam(defaultValue="")String text,@RequestPart(value="photo",required=false)MultipartFile photo) throws Exception {if(photo!=null&&photo.getSize()>2_000_000)throw new IllegalArgumentException("照片最大2MB");return service.assist(mode,orderId,text,photo==null?null:photo.getBytes());}
  @PostMapping("/transcribe") public Object transcribe(@RequestPart("audio")MultipartFile audio) throws Exception {if(audio.getSize()>3_000_000)throw new IllegalArgumentException("录音最大3MB");return service.transcribe(audio.getBytes());}
}
