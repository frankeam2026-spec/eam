package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeDocumentService {
  public static final int MAX_PDF_BYTES=50*1024*1024;
  @org.springframework.beans.factory.annotation.Autowired private com.windasoft.eam.service.equipment.EquipmentQueryService equipmentQueries;
  private final KnowledgeDocumentRepository repository;
  private final KnowledgePdfParser parser;
  private final KnowledgeContentWriter writer;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate transaction;
  private final Path root;
  public KnowledgeDocumentService(KnowledgeDocumentRepository repository,KnowledgePdfParser parser,
      KnowledgeContentWriter writer,AuthContext auth,PermissionService permissions,
      TransactionTemplate transaction,@Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.repository=repository; this.parser=parser; this.writer=writer; this.auth=auth;
    this.permissions=permissions; this.transaction=transaction;
    this.root=Path.of(directory,"knowledge").toAbsolutePath().normalize();
  }

  public Object list(String q,int page,int size) {
    return repository.list(auth.tenantId(),permissions.scope("knowledge","查看"),q,page,size);
  }
  public Object versions(long id,int page,int size){var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");return repository.versions(auth.tenantId(),id,page,size);}
  public Object retryParse(long id,long expected){return transaction.execute(s->{var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");repository.retryParse(auth.tenantId(),id,expected,auth.userId());return Map.of("rowVersion",expected+1);});}
  public Object companies() {
    return Map.of("data",repository.companies(auth.tenantId(),permissions.scope("knowledge","编辑")));
  }
  public Object applicability(long id){var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");return Map.of("all",Boolean.TRUE.equals(doc.get("applies_to_all"))||"1".equals(Objects.toString(doc.get("applies_to_all"))),"equipment",repository.applicability(auth.tenantId(),id),"rowVersion",doc.get("row_version"));}
  public Object bind(long id,long expected,boolean all,List<Long> equipment){
    if(equipment==null||equipment.size()>200||new HashSet<>(equipment).size()!=equipment.size()||(!all&&equipment.isEmpty())||(all&&!equipment.isEmpty()))throw new IllegalArgumentException("请选择通用资料或指定适用设备");
    return transaction.execute(s->{var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new IllegalArgumentException("文档不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");
      for(long eq:equipment){var device=equipmentQueries.get(eq);if(!Objects.toString(doc.get("company_id")).equals(device.companyOrgId()))throw new IllegalArgumentException("适用设备必须属于资料所属公司");}
      repository.bind(auth.tenantId(),id,expected,all,equipment);return Map.of("rowVersion",expected+1);
    });
  }
  public Path original(long id,long version){
    var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在");
    company(((Number)doc.get("company_id")).longValue(),"查看");
    var file=repository.publishedFile(auth.tenantId(),id,version);
    if(file==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料版本已停用或替换");
    String key=file.get("file_key").toString();if(!key.matches("[0-9a-f-]{36}\\.pdf"))throw new IllegalStateException("资料文件标识无效");
    Path path=root.resolve(Long.toString(auth.tenantId())).resolve(key);
    if(!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"原文文件不存在");return path;
  }
  public byte[] pageImage(long id,long version,int page) throws Exception {
    try(var pdf=org.apache.pdfbox.Loader.loadPDF(original(id,version).toFile())){
      if(page<1||page>pdf.getNumberOfPages())throw new IllegalArgumentException("原文页码无效");
      var box=pdf.getPage(page-1).getCropBox();if(box.getWidth()<=0||box.getHeight()<=0||box.getWidth()*box.getHeight()*4>20_000_000)throw new IllegalArgumentException("原文页面尺寸过大");
      var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(page-1,144),"png",out);return out.toByteArray();
    }
  }

  private void company(long id,String action) {
    var scope=permissions.scope("knowledge",action);
    if (!scope.all() && !scope.companyIds().contains(id))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权访问该公司知识资料");
    if (!repository.companyExists(auth.tenantId(),id))
      throw new IllegalArgumentException("所属公司不存在或已停用");
  }

  public Object pages(long id) {
    var doc=repository.document(auth.tenantId(),id);
    if(doc==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识文档不存在");
    company(((Number)doc.get("company_id")).longValue(),"查看");
    // Draft extraction is management-only until publishing and retrieval authorization are connected.
    company(((Number)doc.get("company_id")).longValue(),"编辑");
    return Map.of("data",repository.pages(auth.tenantId(),id));
  }

  public Object upload(long company,String title,byte[] bytes) throws Exception {
    return upload(company,title,bytes,"MANUAL");
  }
  public Object upload(long company,String title,byte[] bytes,String type) throws Exception {
    if(!java.util.Set.of("MANUAL","SOP").contains(type))throw new IllegalArgumentException("资料类型无效");
    company(company,"编辑");
    title=title==null ? "" : title.strip();
    if(title.isBlank() || title.length()>200) throw new IllegalArgumentException("文档名称须为1至200个字符");
    if(bytes==null||bytes.length<5||bytes.length>MAX_PDF_BYTES||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("请上传有效PDF，最大50MB");
    long id=IdWorker.getId(),version=IdWorker.getId();
    String key=UUID.randomUUID()+".pdf";
    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    Path directory=root.resolve(Long.toString(auth.tenantId()));
    Files.createDirectories(directory);
    Path target=directory.resolve(key);
    Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
    String name=title;
    try {
      transaction.executeWithoutResult(status -> {
        company(company,"编辑");
        repository.create(auth.tenantId(),auth.userId(),id,version,company,name,key,digest,KnowledgePdfParser.VERSION);
        repository.pending(auth.tenantId(),version,auth.userId());
        repository.type(auth.tenantId(),id,type);
      });
    } catch(Exception error) {
      try { Files.deleteIfExists(target); } catch(Exception cleanup) { error.addSuppressed(cleanup); }
      throw error;
    }
    return Map.of("data",Map.of("id",Long.toString(id),"versionId",Long.toString(version),
        "parseStatus","PENDING"));
  }
  public Object replace(long id,long expected,byte[] bytes) throws Exception {
    var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文档不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");
    if(bytes==null||bytes.length<5||bytes.length>MAX_PDF_BYTES||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("请上传有效PDF，最大50MB");
    long version=IdWorker.getId();String key=UUID.randomUUID()+".pdf",digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    Path dir=root.resolve(Long.toString(auth.tenantId()));Files.createDirectories(dir);Path file=dir.resolve(key);Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
    try{transaction.executeWithoutResult(s->{company(((Number)doc.get("company_id")).longValue(),"编辑");repository.addVersion(auth.tenantId(),id,version,auth.userId(),expected,key,digest,KnowledgePdfParser.VERSION);});}
    catch(Exception error){try{Files.deleteIfExists(file);}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}
    return Map.of("id",Long.toString(id),"versionId",Long.toString(version),"rowVersion",expected+1);
  }
  public Object disable(long id,long expected){
    var doc=repository.document(auth.tenantId(),id);if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文档不存在");company(((Number)doc.get("company_id")).longValue(),"停用");
    if(!repository.disable(auth.tenantId(),id,expected))throw new ResponseStatusException(HttpStatus.CONFLICT,"文档已变更，请刷新");return Map.of("status","DISABLED","rowVersion",expected+1);
  }
  public void parseVersion(Map<String,Object> row,int attempt,com.windasoft.eam.repository.knowledge.KnowledgeJobRepository jobs) throws Exception {
    long tenant=((Number)row.get("tenant_id")).longValue(),version=((Number)row.get("id")).longValue(),id=((Number)row.get("document_id")).longValue();
    if(auth.tenantId()!=tenant)throw new IllegalStateException("解析任务租户不符");
    var doc=repository.document(tenant,id);if(doc==null)throw new IllegalArgumentException("文档不存在");company(((Number)doc.get("company_id")).longValue(),"编辑");
    String key=row.get("file_key").toString();if(!key.matches("[0-9a-f-]{36}\\.pdf"))throw new IllegalArgumentException("文档文件标识无效");
    Path file=root.resolve(Long.toString(tenant)).resolve(key);if(Files.size(file)>MAX_PDF_BYTES)throw new IllegalArgumentException("文档大小超限");
    var pages=parser.parse(Files.readAllBytes(file),()->jobs.heartbeat(tenant,version,attempt));
    company(((Number)doc.get("company_id")).longValue(),"编辑");writer.commitAttempt(tenant,version,pages,attempt);
  }
}
