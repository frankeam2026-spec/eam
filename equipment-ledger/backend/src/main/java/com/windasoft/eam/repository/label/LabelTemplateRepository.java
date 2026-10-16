package com.windasoft.eam.repository.label;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.label.LabelTemplate;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class LabelTemplateRepository {
  private final LabelTemplateMapper templates;
  private final EquipmentMapper equipment;
  private final RepairOrderMapper repairs;

  public LabelTemplateRepository(
      LabelTemplateMapper templates, EquipmentMapper equipment, RepairOrderMapper repairs) {
    this.templates = templates;
    this.equipment = equipment;
    this.repairs = repairs;
  }

  public List<LabelTemplate> list(long tenant) {
    return templates.selectList(
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .orderByDesc(LabelTemplate::getUpdatedAt));
  }

  public LabelTemplate find(long tenant, Long id) {
    return templates.selectOne(
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .eq(LabelTemplate::getId, id));
  }

  public long nameCount(long tenant, String name, Long current) {
    var q =
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .eq(LabelTemplate::getName, name);
    if (current != null) q.ne(LabelTemplate::getId, current);
    return templates.selectCount(q);
  }

  public void insert(LabelTemplate row) {
    templates.insert(row);
  }

  public int update(LabelTemplate row) {
    return templates.updateById(row);
  }

  public void delete(Long id) {
    templates.deleteById(id);
  }

  public Equipment equipment(long tenant, Long id) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getId, id));
  }

  public Equipment equipmentById(Long id) {
    return equipment.selectById(id);
  }

  public List<RepairOrder> repairs(long tenant, String code) {
    return repairs.selectList(
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getEquipmentCode, code)
            .orderByDesc(RepairOrder::getReportedAt)
            .last("LIMIT 1001"));
  }
}
