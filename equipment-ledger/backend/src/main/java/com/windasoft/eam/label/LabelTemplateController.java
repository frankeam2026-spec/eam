package com.windasoft.eam.label;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.system.*;
import com.windasoft.eam.equipment.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/label-templates")
public class LabelTemplateController {
 private final com.windasoft.eam.repair.RepairOrderMapper repairs;private final com.windasoft.eam.printing.GridPdfService pdf;private final LabelTemplateMapper mapper;private final AuthContext auth;private final PermissionService permissions;private final ObjectMapper json;private final EquipmentMapper equipment;
 public LabelTemplateController(LabelTemplateMapper mapper,AuthContext auth,PermissionService permissions,ObjectMapper json,EquipmentMapper equipment,com.windasoft.eam.printing.GridPdfService pdf,com.windasoft.eam.repair.RepairOrderMapper repairs){this.repairs=repairs;this.pdf=pdf;this.mapper=mapper;this.auth=auth;this.permissions=permissions;this.json=json;this.equipment=equipment;}
 public record Form(@NotBlank @Size(max=100) String name,@NotNull @DecimalMin("10") @DecimalMax("600") BigDecimal widthMm,@NotNull @DecimalMin("10") @DecimalMax("600") BigDecimal heightMm,@Size(max=500) String remark,JsonNode elements,JsonNode layout,Integer rowVersion){}
 @GetMapping public Map<String,Object> list(){permissions.require("equipmentTag","查看");return Map.of("data",mapper.selectList(new LambdaQueryWrapper<LabelTemplate>().eq(LabelTemplate::getTenantId,auth.tenantId()).orderByDesc(LabelTemplate::getUpdatedAt)).stream().map(this::view).toList());}
 @PostMapping public Map<String,Object> create(@Valid @RequestBody Form f){permissions.require("equipmentTag","新增");var row=new LabelTemplate();row.setTenantId(auth.tenantId());row.setDeleted(0);row.setRowVersion(0);assign(row,f);mapper.insert(row);return Map.of("data",view(require(row.getId())));}
 @PutMapping("/{id}") public Map<String,Object> update(@PathVariable Long id,@Valid @RequestBody Form f){permissions.require("equipmentTag","编辑");var row=require(id);assign(row,f);if(f.rowVersion()==null)throw new IllegalArgumentException("缺少版本号");row.setRowVersion(f.rowVersion());if(mapper.updateById(row)!=1)throw new IllegalArgumentException("模板已被修改，请重新打开");return Map.of("data",view(require(id)));}
 @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable Long id){permissions.require("equipmentTag","删除");require(id);mapper.deleteById(id);return Map.of("data",true);}
 public record PrintForm(@NotEmpty @Size(max=200) List<Long> equipmentIds){}
 @PostMapping("/{id}/print-data") public Map<String,Object> printData(@PathVariable Long id,@Valid @RequestBody PrintForm f){permissions.require("equipmentTag","打印");var template=require(id);var rows=f.equipmentIds().stream().distinct().map(key->{var e=equipment.selectOne(new LambdaQueryWrapper<Equipment>().eq(Equipment::getTenantId,auth.tenantId()).eq(Equipment::getId,key));if(e==null)throw new IllegalArgumentException("设备不存在");permissions.checkEquipment(e,"查看");return Map.of("name",safe(e.getName()),"code",safe(e.getEquipmentCode()),"owner",safe(e.getOwnerName()),"department",safe(e.getDepartment()),"model",safe(e.getModel()));}).toList();return Map.of("template",view(template),"equipment",rows);}
 private LabelTemplate require(Long id){var row=mapper.selectOne(new LambdaQueryWrapper<LabelTemplate>().eq(LabelTemplate::getTenantId,auth.tenantId()).eq(LabelTemplate::getId,id));if(row==null)throw new IllegalArgumentException("模板不存在");return row;}
 private void assign(LabelTemplate row,Form f){if(f.layout()!=null&&!f.layout().isNull())com.windasoft.eam.printing.GridValidator.validate(f.layout(),f.widthMm().doubleValue(),f.heightMm().doubleValue());else validateElements(f);var q=new LambdaQueryWrapper<LabelTemplate>().eq(LabelTemplate::getTenantId,auth.tenantId()).eq(LabelTemplate::getName,f.name().trim());if(row.getId()!=null)q.ne(LabelTemplate::getId,row.getId());if(mapper.selectCount(q)>0)throw new IllegalArgumentException("模板名称已存在");row.setName(f.name().trim());row.setWidthMm(f.widthMm());row.setHeightMm(f.heightMm());row.setRemark(f.remark()==null?"":f.remark());row.setElementsJson(f.elements()==null?"[]":f.elements().toString());row.setLayoutJson(f.layout()==null||f.layout().isNull()?null:f.layout().toString());}
 private void validateElements(Form f){var elements=f.elements();if(elements==null||!elements.isArray()||elements.size()>100||elements.toString().length()>3000000)throw new IllegalArgumentException("模板内容过大或格式错误");Set<String> ids=new HashSet<>();for(var e:elements){if(!ids.add(e.path("id").asText())||e.path("id").asText().isBlank())throw new IllegalArgumentException("元素编号无效");if(!Set.of("name","code","owner","department","model","text","image").contains(e.path("type").asText()))throw new IllegalArgumentException("无效内容类型");double x=number(e,"x",0,300),y=number(e,"y",0,300),w=number(e,"width",1,300),h=number(e,"height",1,300);if(x+w>f.widthMm().doubleValue()+0.01||y+h>f.heightMm().doubleValue()+0.01)throw new IllegalArgumentException("内容超出标签边界");number(e,"fontSize",6,72);if(e.path("prefix").asText().length()>100||e.path("text").asText().length()>1000)throw new IllegalArgumentException("文本过长");String image=e.path("image").asText();if(!image.isEmpty()&&!image.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+"))throw new IllegalArgumentException("图片仅支持PNG、JPEG、WebP");}}
 private double number(JsonNode e,String key,double min,double max){var n=e.path(key);double value=n.asDouble(Double.NaN);if(!n.isNumber()||!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("无效元素尺寸或字号");return value;}
 private Map<String,Object> view(LabelTemplate row){try{var result=new LinkedHashMap<String,Object>();result.put("id",String.valueOf(row.getId()));result.put("name",row.getName());result.put("widthMm",row.getWidthMm());result.put("heightMm",row.getHeightMm());result.put("remark",safe(row.getRemark()));result.put("elements",json.readTree(row.getElementsJson()));result.put("layout",row.getLayoutJson()==null?null:json.readTree(row.getLayoutJson()));result.put("rowVersion",row.getRowVersion());result.put("updatedAt",row.getUpdatedAt()==null?"":row.getUpdatedAt().toString());return result;}catch(Exception e){throw new IllegalStateException("模板内容读取失败",e);}}
 public record PdfForm(@NotEmpty @Size(max=200) List<Long> equipmentIds,@Min(1) @Max(20) int copies){}
 @PostMapping("/{id}/pdf") public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable Long id,@Valid @RequestBody PdfForm f)throws java.io.IOException{
  permissions.require("equipmentTag","打印");var t=require(id);List<Map<String,String>> data=f.equipmentIds().stream().distinct().map(key->{var e=equipment.selectOne(new LambdaQueryWrapper<Equipment>().eq(Equipment::getTenantId,auth.tenantId()).eq(Equipment::getId,key));if(e==null)throw new IllegalArgumentException("设备不存在");permissions.checkEquipment(e,"查看");return equipmentData(e);}).toList();
  List<List<Map<String,String>>> details=new ArrayList<>();if(t.getLayoutJson()!=null&&json.readTree(t.getLayoutJson()).path("repeat").isObject())for(Long key:f.equipmentIds().stream().distinct().toList()){var e=equipment.selectById(key);var records=repairs.selectList(new LambdaQueryWrapper<com.windasoft.eam.repair.RepairOrder>().eq(com.windasoft.eam.repair.RepairOrder::getTenantId,auth.tenantId()).eq(com.windasoft.eam.repair.RepairOrder::getEquipmentCode,e.getEquipmentCode()).orderByDesc(com.windasoft.eam.repair.RepairOrder::getFaultAt).last("LIMIT 1001"));if(records.size()>1000)throw new IllegalArgumentException("单设备维修明细超过1000条，请缩小打印范围");details.add(records.stream().filter(r->{try{permissions.checkRepair(r,"查看");return true;}catch(org.springframework.web.server.ResponseStatusException denied){return false;}}).map(r->Map.of("repair_orderNo",safe(r.getOrderNo()),"repair_faultAt",r.getFaultAt()==null?"":r.getFaultAt().toString().replace('T',' '),"repair_description",safe(r.getDescription()),"repair_assignee",safe(r.getAssignee()),"repair_status",safe(r.getStatus()))).toList());}
  return response(pdf.render(t.getWidthMm().doubleValue(),t.getHeightMm().doubleValue(),t.getLayoutJson()==null?null:json.readTree(t.getLayoutJson()),json.readTree(t.getElementsJson()),data,f.copies(),details));
 }
 @PostMapping("/preview") public org.springframework.http.ResponseEntity<byte[]> preview(@Valid @RequestBody Form f)throws java.io.IOException{
  permissions.require("equipmentTag","查看");if(f.layout()!=null&&!f.layout().isNull())com.windasoft.eam.printing.GridValidator.validate(f.layout(),f.widthMm().doubleValue(),f.heightMm().doubleValue());else validateElements(f);
  return response(pdf.render(f.widthMm().doubleValue(),f.heightMm().doubleValue(),f.layout(),f.elements(),List.of(Map.ofEntries(Map.entry("name","数控车床"),Map.entry("code","EQ-2026-0001"),Map.entry("owner","张建国"),Map.entry("department","机加工一部"),Map.entry("model","CK6150"),Map.entry("category","机床设备"),Map.entry("serial","CK6150-2024-031"),Map.entry("brand","沈阳机床"),Map.entry("functionLocation","A车间"),Map.entry("assetNo","ZC-0001"),Map.entry("unit","台"),Map.entry("supplier","设备供应商"),Map.entry("level","A级"),Map.entry("inspectorName","李明"),Map.entry("purchaseDate","2024-01-15"),Map.entry("inServiceDate","2024-02-01"),Map.entry("calibrationDate","2026-10-10"),Map.entry("calibrationValidUntil","2027-10-10"),Map.entry("company","苏州风塔精密制造有限公司"),Map.entry("state","正常"))),1,List.of(List.of(Map.of("repair_orderNo","WX-2026-0001","repair_faultAt","2026-10-10 09:00","repair_description","主轴运行时有异响","repair_assignee","王师傅","repair_status","已完成")))));
 }
 private org.springframework.http.ResponseEntity<byte[]> response(byte[] data){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF).header("Cache-Control","no-store").header("Content-Disposition","inline; filename=equipment-labels.pdf").body(data);}
 private Map<String,String> equipmentData(Equipment e){var d=new LinkedHashMap<String,String>();
d.put("name",safe(e.getName()));
d.put("code",safe(e.getEquipmentCode()));
d.put("owner",safe(e.getOwnerName()));
d.put("department",safe(e.getDepartment()));
d.put("model",safe(e.getModel()));
d.put("company",safe(e.getCompany()));
d.put("category",safe(e.getCategory()));
d.put("state",safe(e.getEquipmentStatus()));
d.put("serial",safe(e.getSerialNo()));
d.put("brand",safe(e.getBrand()));
d.put("functionLocation",safe(e.getFunctionLocation()));
d.put("assetNo",safe(e.getAssetNo()));
d.put("unit",safe(e.getUnitName()));
d.put("supplier",safe(e.getSupplier()));
d.put("level",safe(e.getEquipmentLevel()));
d.put("inspectorName",safe(e.getInspectorName()));
d.put("purchaseDate",e.getPurchaseDate()==null?"":e.getPurchaseDate().toString());
d.put("inServiceDate",e.getInServiceDate()==null?"":e.getInServiceDate().toString());
d.put("calibrationDate",e.getCalibrationDate()==null?"":e.getCalibrationDate().toString());
d.put("calibrationValidUntil",e.getCalibrationValidUntil()==null?"":e.getCalibrationValidUntil().toString());
return d;}
 private String safe(String value){return value==null?"":value;}
}
