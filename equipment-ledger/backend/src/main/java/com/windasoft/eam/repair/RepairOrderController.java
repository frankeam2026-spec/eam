package com.windasoft.eam.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.equipment.Equipment;
import com.windasoft.eam.equipment.EquipmentMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.system.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repairs")
public class RepairOrderController {
    @org.springframework.beans.factory.annotation.Autowired private RepairWorkflowService workflow;
    private final RepairOrderMapper mapper;
    private final RepairQueryService queries;
    private final NumberCounterMapper counter;
    private final ObjectMapper objectMapper;
    private final EquipmentMapper equipmentMapper;
    private final AuthContext auth;
    private final PermissionService permissions;
    @Value("${eam.upload.directory:./data/uploads}") private String uploadDirectory;
    public RepairOrderController(RepairOrderMapper mapper, NumberCounterMapper counter, ObjectMapper objectMapper,EquipmentMapper equipmentMapper,AuthContext auth,PermissionService permissions, RepairQueryService queries) { this.mapper=mapper; this.counter=counter; this.objectMapper=objectMapper;this.equipmentMapper=equipmentMapper;this.auth=auth;this.permissions=permissions;this.queries=queries; }

    public record Form(@NotBlank(message="设备编号必填") String equipmentCode, @NotBlank(message="设备名称必填") String equipmentName,
        String department, @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm") @NotNull(message="故障时间必填") LocalDateTime faultAt,
        @NotBlank(message="故障类型必填") String faultType, @NotBlank(message="紧急程度必填") String severity,
        @NotBlank(message="故障描述必填") String description, @NotBlank(message="报修人必填") String reporter,
        String assignee, String repairAction, Integer downtimeMinutes, JsonNode attachments) {}
    public record Action(@NotBlank(message="操作类型必填") String action, String assignee, String repairAction, Integer downtimeMinutes) {}

    @GetMapping public Map<String,Object> list(@RequestParam Map<String,String> params) {
        var result=queries.list(params);
        return Map.of("data",result.page().getRecords().stream().map(this::view).toList(),
            "page",Map.of("number",result.page().getCurrent(),"size",result.page().getSize(),"total",result.page().getTotal()),
            "counts",result.counts());
    }
    @GetMapping("/export") public void export(@RequestParam Map<String,String> params,jakarta.servlet.http.HttpServletResponse response) throws IOException {
        permissions.require("repair","导出");
        queries.validateExport(params);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition","attachment; filename=repair-orders.csv");
        queries.export(params,response.getWriter());
    }
    @PostMapping("/attachments") public Map<String,Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if(!permissions.allowed("repair","报修")&&!permissions.allowed("repair","处理")&&!permissions.allowed("repair","验收"))permissions.require("repair","查看");
        if(file.isEmpty())throw new IllegalArgumentException("请选择文件");
        if(file.getSize()>20L*1024*1024)throw new IllegalArgumentException("单个附件不能超过20MB");
        String original=file.getOriginalFilename()==null?"附件":Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension="";int dot=original.lastIndexOf('.');if(dot>=0)extension=original.substring(dot).toLowerCase();
        if(!extension.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)?"))throw new IllegalArgumentException("不支持该文件类型");
        String id=UUID.randomUUID().toString();Path directory=Path.of(uploadDirectory).toAbsolutePath().normalize();Files.createDirectories(directory);Path target=directory.resolve(id+extension).normalize();
        if(!target.getParent().equals(directory))throw new IllegalArgumentException("文件名无效");file.transferTo(target);
        return Map.of("data",Map.of("id",id,"name",original,"contentType",file.getContentType()==null?"application/octet-stream":file.getContentType(),"size",file.getSize(),"url","/api/v1/repairs/attachments/"+id));
    }
    @GetMapping("/attachments/{id}") public ResponseEntity<InputStreamResource> download(@PathVariable String id) throws IOException {
        var matches=mapper.selectList(new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getTenantId,auth.tenantId()).and(w->w.like(RepairOrder::getAttachmentsJson,id).or().like(RepairOrder::getProcessAttachmentsJson,id).or().apply("id IN (SELECT order_id FROM eam_repair_event WHERE tenant_id={0} AND details_json LIKE {1})",auth.tenantId(),"%"+id+"%")));
        if(matches.stream().noneMatch(row->{try{workflow.viewAllowed(row);return true;}catch(org.springframework.web.server.ResponseStatusException ignored){return false;}}))throw new IllegalArgumentException("附件不存在");
        if(!id.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("附件不存在");Path directory=Path.of(uploadDirectory).toAbsolutePath().normalize();
        if(!Files.isDirectory(directory))throw new IllegalArgumentException("附件不存在");List<Path> files;try(var stream=Files.list(directory)){files=stream.filter(p->p.getFileName().toString().startsWith(id+".")).limit(1).toList();}
        if(files.isEmpty())throw new IllegalArgumentException("附件不存在");Path file=files.getFirst();String ext=file.getFileName().toString().substring(id.length()+1);String type=switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "gif"->"image/gif";case "webp"->"image/webp";case "pdf"->"application/pdf";default->"application/octet-stream";};
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CONTENT_DISPOSITION,type.startsWith("image/")?"inline":"attachment").body(new InputStreamResource(Files.newInputStream(file)));
    }
    @PostMapping @Transactional public Map<String,Object> create(@Valid @RequestBody Form form){
        var row=new RepairOrder();copy(form,row);setEquipmentAndReporter(row,form.equipmentCode());permissions.checkRepair(row,"报修");String month=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));counter.nextMonthlyNumber("repair-"+auth.tenantId(),month);row.setOrderNo("wx-"+month+"-"+String.format("%04d",counter.currentValue()));row.setStatus("待派工");row.setReportedAt(LocalDateTime.now());row.setRowVersion(0);row.setDeleted(0);mapper.insert(row);workflow.record(row,"报修",Map.of("description",row.getDescription(),"faultType",row.getFaultType(),"faultAt",row.getFaultAt().toString(),"attachments",json(row.getAttachmentsJson())));return Map.of("data",view(row));
    }
    @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable Long id,@Valid @RequestBody Form form,@RequestParam int rowVersion){
        var row=require(id);permissions.checkRepair(row,"编辑");VersionGuard.check(row.getRowVersion(),rowVersion);if(!"待派工".equals(row.getStatus()))throw new IllegalArgumentException("仅待派工工单可以编辑");copy(form,row);setEquipment(row,form.equipmentCode());permissions.checkRepair(row,"编辑");VersionGuard.updated(mapper.updateById(row));workflow.record(row,"编辑报修",Map.of("description",row.getDescription(),"faultType",row.getFaultType(),"attachments",json(row.getAttachmentsJson())));return Map.of("data",view(require(id)));
    }
    @PostMapping("/{id}/actions") @Transactional public Map<String,Object> action(@PathVariable Long id,@Valid @RequestBody Action request,@RequestParam int rowVersion){
        if(workflow!=null){
            if("complete".equals(request.action()))throw new IllegalArgumentException("请在处理页面填写完整处理记录并提交");
            var body=objectMapper.valueToTree(request);
            if("dispatch".equals(request.action()))((com.fasterxml.jackson.databind.node.ObjectNode)body).set("userIds",objectMapper.valueToTree(List.of(permissions.userIdByName(request.assignee()))));
            if(!"cancel".equals(request.action()))return Map.of("data",view(workflow.action(id,rowVersion,body)));
        }
        var row=require(id);String next;
        String requiredAction=switch(request.action()){case "dispatch"->"派工";case "complete","suspend","resume"->"处理";case "accept","return"->"验收";case "cancel"->"撤回";default->throw new IllegalArgumentException("不支持的工单操作");};
        permissions.checkRepair(row,requiredAction);
        VersionGuard.check(row.getRowVersion(),rowVersion);
        switch(request.action()){
            case "dispatch"->{if(!"待派工".equals(row.getStatus()))throw invalid();if(request.assignee()==null||request.assignee().isBlank())throw new IllegalArgumentException("派工时必须填写维修人");row.setAssignee(request.assignee().trim());row.setAssigneeUserId(permissions.userIdByName(row.getAssignee()));if(row.getAssigneeUserId()==null)throw new IllegalArgumentException("维修人必须是当前租户员工");next="处理中";}
            case "complete"->{if(!"处理中".equals(row.getStatus()))throw invalid();if(request.repairAction()==null||request.repairAction().isBlank())throw new IllegalArgumentException("请先记录维修措施");row.setRepairAction(request.repairAction().trim());row.setDowntimeMinutes(Math.max(0,request.downtimeMinutes()==null?0:request.downtimeMinutes()));row.setCompletedAt(LocalDateTime.now());next="待验收";}
            case "accept"->{if(!"待验收".equals(row.getStatus()))throw invalid();next="已完成";}
            case "return"->{if(!"待验收".equals(row.getStatus()))throw invalid();next="处理中";}
            case "suspend"->{if(!"处理中".equals(row.getStatus()))throw invalid();next="挂起";}
            case "resume"->{if(!"挂起".equals(row.getStatus()))throw invalid();next="处理中";}
            case "cancel"->{if(!"待派工".equals(row.getStatus()))throw invalid();next="已撤销";}
            default->throw new IllegalArgumentException("不支持的工单操作");
        }
        row.setStatus(next);VersionGuard.updated(mapper.updateById(row));if(workflow!=null)workflow.record(row,"撤销工单",Map.of("status",next));return Map.of("data",view(require(id)));
    }
    @GetMapping("/user-choices") public Map<String,Object> userChoices(){return Map.of("data",workflow.userChoices());}
    @GetMapping("/inbox") public Map<String,Object> inbox(){return Map.of("data",workflow.inbox());}
    @PatchMapping("/inbox/{id}/read") public Map<String,Object> readInbox(@PathVariable long id){workflow.markRead(id);return Map.of("data",true);}
    @GetMapping("/{id}/workflow") public Map<String,Object> detail(@PathVariable long id){var result=new java.util.LinkedHashMap<>(workflow.detail(id));result.put("order",view((RepairOrder)result.get("order")));return Map.of("data",result);}
    @PostMapping("/{id}/workflow") public Map<String,Object> workflowAction(@PathVariable long id,@RequestParam int rowVersion,@RequestBody JsonNode body){return Map.of("data",view(workflow.action(id,rowVersion,body)));}
    @GetMapping("/{id}/stocks") public Map<String,Object> stockChoices(@PathVariable long id){return Map.of("data",workflow.stockChoices(id));}
    @GetMapping("/stocks") public Map<String,Object> inventory(){return Map.of("data",workflow.inventory());}
    @PostMapping("/stocks") public Map<String,Object> stock(@RequestBody JsonNode body){workflow.addStock(body);return Map.of("data",true);}
    private RepairOrder require(Long id){var row=mapper.selectOne(new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getTenantId,auth.tenantId()).eq(RepairOrder::getId,id));if(row==null||Integer.valueOf(1).equals(row.getDeleted()))throw new IllegalArgumentException("维修工单不存在");return row;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("工单当前状态不允许此操作");}
    private void copy(Form f,RepairOrder row){row.setFaultAt(f.faultAt());row.setFaultType(f.faultType().trim());row.setSeverity(f.severity());row.setDescription(f.description().trim());row.setAttachmentsJson(workflow==null?jsonString(f.attachments()):workflow.attachments(f.attachments()));}
    private void setEquipmentAndReporter(RepairOrder row,String code){setEquipment(row,code);row.setTenantId(auth.tenantId());row.setReporterUserId(auth.userId());row.setReporter(auth.user().getDisplayName());}
    private void setEquipment(RepairOrder row,String code){
        var equipment=equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>().eq(Equipment::getTenantId,auth.tenantId()).eq(Equipment::getEquipmentCode,code));
        if(equipment==null)throw new IllegalArgumentException("设备不存在");permissions.checkEquipment(equipment,"查看");
        row.setEquipmentCode(equipment.getEquipmentCode());row.setEquipmentName(equipment.getName());row.setDepartment(equipment.getDepartment());row.setDepartmentOrgId(equipment.getDepartmentOrgId());row.setTenantId(auth.tenantId());
    }
    private String jsonString(JsonNode node){try{return node==null||node.isNull()?"[]":objectMapper.writeValueAsString(node);}catch(Exception e){return "[]";}}
    private static boolean has(String s){return s!=null&&!s.isBlank();}
    private static <T> void order(LambdaQueryWrapper<RepairOrder> q,String direction,com.baomidou.mybatisplus.core.toolkit.support.SFunction<RepairOrder,T> field){if("asc".equalsIgnoreCase(direction))q.orderByAsc(field);else q.orderByDesc(field);}
    private Map<String,Object> view(RepairOrder r){return Map.ofEntries(Map.entry("id",String.valueOf(r.getId())),Map.entry("orderNo",safe(r.getOrderNo())),Map.entry("equipmentCode",safe(r.getEquipmentCode())),Map.entry("equipmentName",safe(r.getEquipmentName())),Map.entry("department",safe(r.getDepartment())),Map.entry("faultAt",date(r.getFaultAt())),Map.entry("faultType",safe(r.getFaultType())),Map.entry("severity",safe(r.getSeverity())),Map.entry("description",safe(r.getDescription())),Map.entry("reporter",safe(r.getReporter())),Map.entry("assignee",safe(r.getAssignee())),Map.entry("status",safe(r.getStatus())),Map.entry("repairAction",safe(r.getRepairAction())),Map.entry("downtimeMinutes",r.getDowntimeMinutes()==null?0:r.getDowntimeMinutes()),Map.entry("reportedAt",date(r.getReportedAt())),Map.entry("attachments",json(r.getAttachmentsJson())),Map.entry("faultCause",safe(r.getFaultCause())),Map.entry("repairMethod",safe(r.getRepairMethod())),Map.entry("processingResult",safe(r.getProcessingResult())),Map.entry("preventiveMeasures",safe(r.getPreventiveMeasures())),Map.entry("collaborators",json(r.getCollaboratorsJson())),Map.entry("processAttachments",json(r.getProcessAttachmentsJson())),Map.entry("outsourceVendor",safe(r.getOutsourceVendor())),Map.entry("rowVersion",r.getRowVersion()==null?0:r.getRowVersion()));}
    private Object json(String value){try{return value==null?objectMapper.createArrayNode():objectMapper.readTree(value);}catch(Exception e){return objectMapper.createArrayNode();}}
    private static final DateTimeFormatter MINUTE=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static String date(LocalDateTime d){return d==null?"":d.format(MINUTE);}private static String safe(String s){return s==null?"":s;}
}
