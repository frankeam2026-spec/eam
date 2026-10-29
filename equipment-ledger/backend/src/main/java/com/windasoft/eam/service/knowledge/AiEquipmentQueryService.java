package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AiEquipmentQueryService {
  private final EquipmentQueryService equipment;
  private final PermissionService permissions;
  private final ObjectMapper json;
  public AiEquipmentQueryService(EquipmentQueryService equipment,PermissionService permissions,ObjectMapper json){this.equipment=equipment;this.permissions=permissions;this.json=json;}
  public Object answer(AiQuestionRouter.Intent intent,Long equipmentId) throws Exception {
    var result=query(intent.filters(),equipmentId,1,20);
    String conditions=intent.filters().isEmpty()?"未附加筛选条件":intent.filters().stream().map(f->label(f.field())+(f.operator().equals("eq")?"等于":"包含")+"「"+f.value()+"」").collect(java.util.stream.Collectors.joining("；"));
    String summary="已查询设备台账：在你有权查看的范围内，共 "+result.get("total")+" 台设备。"+conditions+"。"+(equipmentId==null?"":"已限定当前选择的设备。")+"历史会话请重新查询最新结果。";
    if(intent.operation().equals("EQUIPMENT_COUNT"))return Map.of("answer",summary,"citations",List.of());
    return Map.of("answer",summary,"citations",List.of(),"equipmentRecords",result);
  }
  public Map<String,Object> query(List<AiQuestionRouter.Filter> filters,Long equipmentId,int page,int size) throws Exception {
    permissions.require("knowledge","查看");permissions.require("equipment","查看");
    if(page<1||page>10_000_000||!Set.of(20,50,100).contains(size)||filters==null||filters.size()>6)throw new IllegalArgumentException("设备查询条件无效");
    var advanced=new ArrayList<Map<String,Object>>();
    for(var f:filters){
      if(f==null||f.field()==null||f.operator()==null||!AiQuestionRouter.FIELDS.contains(f.field())||!Set.of("eq","contains").contains(f.operator())||f.value()==null||f.value().isBlank()||f.value().length()>100||Set.of("state","useState","category").contains(f.field())&&!f.operator().equals("eq"))throw new IllegalArgumentException("设备查询条件无效");
      String operator=Set.of("state","useState","category").contains(f.field())?"in":f.operator();
      advanced.add(Map.of("field",f.field(),"operator",operator,"values",List.of(f.value())));
    }
    if(equipmentId!=null)advanced.add(Map.of("field","code","operator","eq","values",List.of(equipment.get(equipmentId).code())));
    var request=new EquipmentListQuery(page,size,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"createdAt","desc",json.writeValueAsString(advanced),null,null,null);
    var result=equipment.list(request);
    var rows=result.data().stream().map(row->{var out=new LinkedHashMap<String,Object>();out.put("id",row.id());out.put("code",row.code());out.put("name",row.name());out.put("model",row.model());out.put("state",row.state());out.put("useState",row.useState());return out;}).toList();
    return Map.of("data",rows,"total",result.page().total(),"filters",filters,"equipmentId",equipmentId==null?"":equipmentId.toString());
  }
  static String label(String field){return switch(field){case "name"->"设备名称";case "code"->"设备编号";case "model"->"规格型号";case "brand"->"品牌";case "state"->"设备状态";case "useState"->"使用状态";case "category"->"设备分类";default->throw new IllegalArgumentException("设备筛选字段无效");};}
}
