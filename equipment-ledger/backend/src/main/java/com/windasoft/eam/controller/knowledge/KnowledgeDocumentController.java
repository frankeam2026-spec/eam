package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.service.knowledge.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeDocumentController {
  private final KnowledgeDocumentService service;
  private final com.windasoft.eam.service.knowledge.KnowledgeIndexService indexes;
  public KnowledgeDocumentController(KnowledgeDocumentService service,com.windasoft.eam.service.knowledge.KnowledgeIndexService indexes) { this.service=service;this.indexes=indexes; }
  public record Version(long rowVersion){}
  public record Applicability(long rowVersion,boolean all,java.util.List<Long> equipmentIds){}
  @GetMapping("/{id}/applicability") public Object applicability(@PathVariable long id){return service.applicability(id);}
  @PutMapping("/{id}/applicability") public Object bind(@PathVariable long id,@RequestBody Applicability body){return service.bind(id,body.rowVersion(),body.all(),body.equipmentIds());}
  @GetMapping("/{id}/versions/{version}/original") public org.springframework.http.ResponseEntity<org.springframework.core.io.FileSystemResource> original(@PathVariable long id,@PathVariable long version){return org.springframework.http.ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(org.springframework.http.MediaType.APPLICATION_PDF).body(new org.springframework.core.io.FileSystemResource(service.original(id,version)));}
  @GetMapping("/{id}/versions/{version}/pages/{page}/image") public org.springframework.http.ResponseEntity<byte[]> image(@PathVariable long id,@PathVariable long version,@PathVariable int page) throws Exception {return org.springframework.http.ResponseEntity.ok().header("Cache-Control","private, no-store").contentType(org.springframework.http.MediaType.IMAGE_PNG).body(service.pageImage(id,version,page));}
  @PostMapping("/{id}/index") public Object index(@PathVariable long id,@RequestBody Version body) { return indexes.queue(id,body.rowVersion()); }
  @PostMapping("/{id}/publish") public Object publish(@PathVariable long id,@RequestBody Version body) { return indexes.publish(id,body.rowVersion()); }
  @PostMapping("/{id}/disable") public Object disable(@PathVariable long id,@RequestBody Version body){return service.disable(id,body.rowVersion());}
  @PostMapping("/{id}/versions") public Object replace(@PathVariable long id,@RequestParam long rowVersion,@RequestPart("file")MultipartFile file) throws Exception {if(file.getSize()>KnowledgeDocumentService.MAX_PDF_BYTES)throw new IllegalArgumentException("PDF最大50MB");return service.replace(id,rowVersion,file.getBytes());}
  @GetMapping("/{id}/versions") public Object versions(@PathVariable long id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){if(page<1||!java.util.Set.of(20,50,100).contains(size))throw new IllegalArgumentException("分页参数无效");return service.versions(id,page,size);}
  @PostMapping("/{id}/retry-parse") public Object retryParse(@PathVariable long id,@RequestBody Version body){return service.retryParse(id,body.rowVersion());}
  @GetMapping("/companies") public Object companies() { return service.companies(); }
  @GetMapping public Object list(@RequestParam(defaultValue="") String q,
      @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
    if(q.length()>200 || page<1 || !java.util.Set.of(20,50,100).contains(size))
      throw new IllegalArgumentException("查询参数无效");
    return service.list(q,page,size);
  }
  @GetMapping("/{id}/pages") public Object pages(@PathVariable long id) { return service.pages(id); }
  @PostMapping public Object upload(@RequestParam long companyId,@RequestParam String title,@RequestParam(defaultValue="MANUAL")String documentType,
      @RequestPart("file") MultipartFile file) throws Exception {
    if(file.isEmpty() || file.getSize()>KnowledgeDocumentService.MAX_PDF_BYTES) throw new IllegalArgumentException("PDF文件最大50MB");
    return service.upload(companyId,title,file.getBytes(),documentType);
  }
}
