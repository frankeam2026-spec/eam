package com.windasoft.eam.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import java.io.PrintWriter;
import com.windasoft.eam.common.AdvancedFilters;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairQueryService {
    private static final List<String> QUEUES = List.of("pending", "owned", "mine", "accept", "created", "dispatch", "all");
    private static final Map<String,SFunction<RepairOrder,?>> FIELDS = Map.ofEntries(
        Map.entry("orderNo", RepairOrder::getOrderNo), Map.entry("equipmentCode", RepairOrder::getEquipmentCode),
        Map.entry("equipmentName", RepairOrder::getEquipmentName), Map.entry("description", RepairOrder::getDescription),
        Map.entry("faultAt", RepairOrder::getFaultAt), Map.entry("department", RepairOrder::getDepartment),
        Map.entry("reporter", RepairOrder::getReporter), Map.entry("assignee", RepairOrder::getAssignee),
        Map.entry("severity", RepairOrder::getSeverity), Map.entry("status", RepairOrder::getStatus),
        Map.entry("reportedAt", RepairOrder::getReportedAt));
    private static final Map<String, AdvancedFilters.Field<RepairOrder>> ADVANCED_FIELDS = advancedFields();
    private static Map<String, AdvancedFilters.Field<RepairOrder>> advancedFields() {
        Map<String, AdvancedFilters.Field<RepairOrder>> result = new HashMap<>();
        FIELDS.forEach((key, column) -> result.put(key, new AdvancedFilters.Field<>(column,
            List.of("faultAt", "reportedAt").contains(key) ? AdvancedFilters.Kind.DATE :
            List.of("status", "severity").contains(key) ? AdvancedFilters.Kind.CHOICE : AdvancedFilters.Kind.TEXT)));
        result.put("faultType", new AdvancedFilters.Field<>(RepairOrder::getFaultType, AdvancedFilters.Kind.TEXT));
        result.put("repairAction", new AdvancedFilters.Field<>(RepairOrder::getRepairAction, AdvancedFilters.Kind.TEXT));
        result.put("acceptanceComment", new AdvancedFilters.Field<>(RepairOrder::getAcceptanceComment, AdvancedFilters.Kind.TEXT));
        result.put("completedAt", new AdvancedFilters.Field<>(RepairOrder::getCompletedAt, AdvancedFilters.Kind.DATE));
        result.put("downtimeMinutes", new AdvancedFilters.Field<>(RepairOrder::getDowntimeMinutes, AdvancedFilters.Kind.NUMBER));
        return Map.copyOf(result);
    }
    private static final List<String> EXPORT_FIELDS=List.of("orderNo","equipmentCode","equipmentName","description","faultAt","department","reporter","assignee","severity","status","reportedAt");
    private static final List<String> EXPORT_LABELS=List.of("工单编号","设备编号","设备名称","故障描述","故障时间","所属部门","报修人","维修人","紧急程度","工单状态","报修时间");
    private final RepairOrderMapper mapper;
    private final AuthContext auth;
    private final PermissionService permissions;
    private final ObjectMapper json;

    public RepairQueryService(RepairOrderMapper mapper, AuthContext auth, PermissionService permissions, ObjectMapper json) {
        this.mapper=mapper; this.auth=auth; this.permissions=permissions; this.json=json;
    }

    public record Result(Page<RepairOrder> page, Map<String,Long> counts) {}

    @Transactional(readOnly=true)
    public Result list(Map<String,String> params) {
        var filters=filters(params);
        long page=number(params.getOrDefault("page","1"),1,10_000_000);
        long size=number(params.getOrDefault("size","10"),1,200);
        var result=mapper.selectPage(new Page<RepairOrder>(page,size), ordered(params,filters,false));
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String queue:QUEUES) counts.put(queue,mapper.selectCount(query(params,filters,queue,false)));
        return new Result(result,counts);
    }

    @Transactional(readOnly=true)
    public void export(Map<String,String> params, PrintWriter writer) {
        permissions.require("repair","导出");
        var filters=filters(params);
        var query=ordered(params,filters,true);
        List<String> columns=exportColumns(params);
        writer.print('\ufeff');
        writer.println(String.join(",",columns.stream().map(key->csv(EXPORT_LABELS.get(EXPORT_FIELDS.indexOf(key)))).toList()));
        long page=1;
        while(true) {
            var result=mapper.selectPage(new Page<RepairOrder>(page++,500,false),query);
            for(var row:result.getRecords()) writer.println(String.join(",",columns.stream().map(key->{
                Object value=FIELDS.get(key).apply(row);
                return csv(value instanceof LocalDateTime d?date(d):value==null?"":String.valueOf(value));
            }).toList()));
            if(writer.checkError()) throw new IllegalStateException("导出连接已断开");
            if(result.getRecords().size()<500) break;
        }
    }

    public void validateExport(Map<String,String> params) {
        permissions.require("repair","导出");
        ordered(params,filters(params),true);
        exportColumns(params);
    }

    private List<String> exportColumns(Map<String,String> params) {
        List<String> columns=params.containsKey("columns")?Arrays.asList(params.get("columns").split(",",-1)):EXPORT_FIELDS;
        if(columns.isEmpty() || columns.size()>FIELDS.size() || new HashSet<>(columns).size()!=columns.size() || !FIELDS.keySet().containsAll(columns)) throw new IllegalArgumentException("导出字段无效");
        return columns;
    }

    private LambdaQueryWrapper<RepairOrder> ordered(Map<String,String> params,Map<String,String> filters,boolean export) {
        var query=query(params,filters,params.getOrDefault("queue","all"),export);
        String sort=params.getOrDefault("sortBy","reportedAt"), direction=params.getOrDefault("direction","desc");
        if(!FIELDS.containsKey(sort)||!List.of("asc","desc").contains(direction)) throw new IllegalArgumentException("排序条件无效");
        query.orderBy(true,"asc".equals(direction),FIELDS.get(sort)).orderByAsc(RepairOrder::getId);
        return query;
    }

    private LambdaQueryWrapper<RepairOrder> query(Map<String,String> params,Map<String,String> filters,String queue,boolean export) {
        if(!QUEUES.contains(queue)) throw new IllegalArgumentException("工单队列无效");
        String keyword=text(params.get("keyword"));
        var q=new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getTenantId,auth.tenantId()).eq(RepairOrder::getDeleted,0);
        if(!keyword.isEmpty()) q.and(w->w.like(RepairOrder::getOrderNo,keyword).or().like(RepairOrder::getEquipmentCode,keyword)
            .or().like(RepairOrder::getEquipmentName,keyword).or().like(RepairOrder::getDescription,keyword)
            .or().like(RepairOrder::getDepartment,keyword).or().like(RepairOrder::getFaultType,keyword)
            .or().like(RepairOrder::getReporter,keyword).or().like(RepairOrder::getAssignee,keyword));
        for(String field:List.of("status","severity","department")) {
            String value=text(params.get(field)); if(!value.isEmpty()) q.eq(FIELDS.get(field),value);
        }
        for(var entry:filters.entrySet()) {
            String value=text(entry.getValue()); if(value.isEmpty()) continue;
            switch(entry.getKey()) {
                case "faultAt","reportedAt" -> q.apply("DATE_FORMAT("+(entry.getKey().equals("faultAt")?"fault_at":"reported_at")+", '%Y-%m-%d %H:%i') LIKE {0}","%"+value.replace('T',' ')+"%");
                case "status","severity" -> q.eq(FIELDS.get(entry.getKey()),value);
                default -> q.like(FIELDS.get(entry.getKey()),value);
            }
        }
        AdvancedFilters.apply(q, params.get("advanced"), ADVANCED_FIELDS, json);
        permissions.filterRepair(q);
        if(export) permissions.filterRepair(q,"导出");
        switch(queue) {
            case "owned" -> q.eq(RepairOrder::getAssigneeUserId,auth.userId());
            case "mine" -> q.eq(RepairOrder::getAssigneeUserId,auth.userId()).in(RepairOrder::getStatus,List.of("处理中","挂起"));
            case "pending" -> q.eq(RepairOrder::getStatus,"待接单").apply("id IN (SELECT order_id FROM eam_repair_candidate WHERE tenant_id={0} AND user_id={1} AND status='待接单')",auth.tenantId(),auth.userId());
            case "accept" -> {q.eq(RepairOrder::getStatus,"待验收");permissions.filterRepairAcceptance(q);}
            case "created" -> q.eq(RepairOrder::getReporterUserId,auth.userId());
            case "dispatch" -> q.eq(RepairOrder::getStatus,"待派工");
            default -> {}
        }
        return q;
    }

    private Map<String,String> filters(Map<String,String> params) {
        String raw=params.getOrDefault("filters","{}");
        if(raw.length()>5000) throw new IllegalArgumentException("筛选条件过长");
        try {
            Map<String,String> result=json.readValue(raw,new TypeReference<Map<String,String>>(){});
            if(result==null || !FIELDS.keySet().containsAll(result.keySet())) throw new IllegalArgumentException("筛选字段无效");
            result.values().forEach(RepairQueryService::text);
            return result;
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("筛选条件格式无效"); }
    }
    private static long number(String raw,long min,long max) {
        try { long value=Long.parseLong(raw); if(value<min||value>max) throw new IllegalArgumentException("分页参数无效"); return value; }
        catch(NumberFormatException e) { throw new IllegalArgumentException("分页参数无效"); }
    }
    private static String text(String value) {
        String result=value==null?"":value.trim();
        if(result.length()>200) throw new IllegalArgumentException("查询条件不能超过200个字符");
        return result;
    }
    static String csv(String value) {
        String text=value==null?"":value;
        String trimmed=text.stripLeading();
        if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0 || text.startsWith("\t") || text.startsWith("\r")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    private static String date(LocalDateTime value) { return value==null?"":value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
}
