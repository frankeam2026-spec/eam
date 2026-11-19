package com.windasoft.eam.service.knowledge;

import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.repair.RepairRecordPage;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.repair.RepairQueryService;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** A bounded, read-only business tool. Data never goes through a generation model. */
@Service
public class AiRepairQueryService {
  private final RepairQueryService repairs;
  private final EquipmentQueryService equipment;
  private final PermissionService permissions;
  public AiRepairQueryService(RepairQueryService repairs,EquipmentQueryService equipment,PermissionService permissions){this.repairs=repairs;this.equipment=equipment;this.permissions=permissions;}
  public record Criteria(LocalDate from,LocalDate to,String relation,boolean completed,Long equipmentId){}
  static Criteria interpret(String text,LocalDate today,Long equipmentId){
    String q=text.replaceAll("[\\s，。？！?!,、]","");
    LocalDate from=null,to=today;
    String period=null;
    for(String phrase:List.of("最近一个月","近一个月","最近1个月","近1个月","上个月","上月","这个月","本月","最近30天","近30天","最近7天","近7天"))if(q.contains(phrase)){period=phrase;break;}
    if(period==null)return null;
    if(Set.of("上个月","上月").contains(period)){to=today.withDayOfMonth(1).minusDays(1);from=to.withDayOfMonth(1);}
    else if(Set.of("这个月","本月").contains(period))from=today.withDayOfMonth(1);
    else if(period.contains("30天"))from=today.minusDays(29);
    else if(period.contains("7天"))from=today.minusDays(6);
    else from=today.minusMonths(1);
    q=q.replace(period,"");
    String relation="";
    if(q.contains("我负责")){relation="owned";q=q.replace("我负责","");}
    else if(q.contains("我发起")||q.contains("我报修")){relation="created";q=q.replace("我发起","").replace("我报修","");}
    boolean completed=q.contains("已完成");q=q.replace("已完成","");
    q=q.replaceAll("请|帮我|给我|查一下|查询|查看|显示|列出|看看|一下|全部|所有|维修工单|报修工单|维修记录|报修记录|维修明细|报修明细|维修清单|维修列表|工单|记录|明细|清单|列表|的","");
    // Unrecognized people, organizations, statuses and dates must not silently disappear.
    if(!q.isEmpty())return null;
    return new Criteria(from,to,relation,completed,equipmentId);
  }
  public Object tryAnswer(String question,Long equipmentId) throws Exception {
    if(!question.matches("(?s).*(维修|报修|工单).*(记录|明细|清单|列表|工单).*"))return null;
    if(question.matches("(?s).*(排行|排名|统计|趋势|分布|执行率|故障率|工作量).*"))return null;
    if(question.matches("(?s).*(怎么|如何|怎样|填写|录入|编写|流程|步骤|手册|SOP).*"))return null;
    if(!permissions.allowed("repair",PermissionAction.VIEW))return Map.of("answer","你没有维修工单查看权限，请联系管理员。","citations",List.of());
    var criteria=interpret(question,LocalDate.now(ZoneId.of("Asia/Shanghai")),equipmentId);
    if(criteria==null)return Map.of("answer","请明确维修记录的查询条件。目前可查最近一个月、最近30天、最近7天、本月或上个月；也可指定我负责、我发起或已完成。公司、人员等其他条件请在维修工单列表筛选。","citations",List.of());
    var data=query(criteria,1,20);
    return Map.of("answer","已查询系统维修工单，时间口径和结果见下表。历史会话请重新查询最新记录。","citations",List.of(),"repairRecords",data);
  }
  public RepairRecordPage query(Criteria criteria, int page, int size) {
    permissions.require("knowledge", PermissionAction.VIEW);
    permissions.require("repair", PermissionAction.VIEW);
    if (criteria.from() == null || criteria.to() == null || criteria.from().isAfter(criteria.to())
        || criteria.from().isBefore(criteria.to().minusYears(2))
        || criteria.to().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))
        || page < 1 || page > 10_000_000 || !Set.of(20, 50, 100).contains(size)
        || !Set.of("", "owned", "created").contains(criteria.relation())) {
      throw new IllegalArgumentException("维修记录查询条件无效");
    }
    var equipmentId = criteria.equipmentId();
    // 先核验设备查看权限，再将已授权设备的身份交给维修查询。
    var code = equipmentId == null ? null : equipment.get(equipmentId).code();
    var result = repairs.pageForAssistant(criteria.from(), criteria.to(), criteria.relation(),
        criteria.completed(), equipmentId, code, page, size);
    var rows = result.getRecords().stream()
        .map(row -> new RepairRecordPage.Row(row.getId().toString(), row.getOrderNo(),
            row.getEquipmentName(), row.getEquipmentCode(), row.getStatus(),
            row.getReportedAt(), row.getCompletedAt()))
        .toList();
    var context = new RepairRecordPage.Criteria(criteria.from().toString(), criteria.to().toString(),
        criteria.relation(), criteria.completed(), equipmentId == null ? "" : equipmentId.toString());
    return new RepairRecordPage(rows, result.getTotal(), context,
        criteria.completed() ? "completedAt" : "reportedAt");
  }
}
