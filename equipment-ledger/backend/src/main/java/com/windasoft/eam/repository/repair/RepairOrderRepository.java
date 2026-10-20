package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class RepairOrderRepository {
  private final RepairRelationsRepository collaborators;
  private final RepairOrderMapper orders;
  private final EquipmentMapper equipment;
  private final com.windasoft.eam.repository.basedata.BaseItemMapper base;
  private final NumberCounterMapper counter;
  private final String directory;

  public RepairOrderRepository(
      RepairOrderMapper orders,
      EquipmentMapper equipment,
      com.windasoft.eam.repository.basedata.BaseItemMapper base,
      NumberCounterMapper counter,
      @Value("${eam.upload.directory:./data/uploads}") String directory,
      RepairRelationsRepository collaborators) {
    this.collaborators = collaborators;
    this.orders = orders;
    this.equipment = equipment;
    this.base = base;
    this.counter = counter;
    this.directory = directory;
  }

  public RepairOrder find(long tenant, Long id) {
    return collaborators.hydrate(
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .eq(RepairOrder::getId, id)));
  }

  public Equipment equipment(long tenant, String code) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code));
  }

  public void insert(RepairOrder row) {
    orders.insert(row);
    collaborators.save(row);
  }

  public int update(RepairOrder row) {
    int changed = orders.updateById(row);
    if (changed > 0) collaborators.save(row);
    return changed;
  }

  public void nextMonthlyNumber(String prefix, String month) {
    counter.nextMonthlyNumber(prefix, month);
  }

  public long currentValue() {
    return counter.currentValue();
  }

  public List<RepairOrder> attachmentOrders(long tenant, String id) {
    return collaborators.hydrate(
        orders.selectList(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .and(
                    w ->
                        w.apply(
                                "(EXISTS(SELECT 1 FROM eam_repair_report_file f WHERE"
                                    + " f.tenant_id={0} AND f.order_id=eam_repair_order.id AND"
                                    + " f.file_id={1}) OR EXISTS(SELECT 1 FROM"
                                    + " eam_repair_process_file f WHERE f.tenant_id={0} AND"
                                    + " f.order_id=eam_repair_order.id AND f.file_id={1}))",
                                tenant, id)
                            .or()
                            .apply(
                                "id IN (SELECT order_id FROM eam_repair_event WHERE tenant_id={0}"
                                    + " AND details_json LIKE {1})",
                                tenant, "%" + id + "%"))));
  }

  public void store(InputStream input, String id, String extension) throws IOException {
    Path root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
    Path target = root.resolve(id + extension).normalize();
    if (!target.getParent().equals(root))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0010");
    Files.copy(input, target);
  }

  public Path attachmentPath(String id) throws IOException {
    Path root = Path.of(directory).toAbsolutePath().normalize();
    if (!Files.isDirectory(root))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0011");
    List<Path> files;
    try (var stream = Files.list(root)) {
      files = stream.filter(p -> p.getFileName().toString().startsWith(id + ".")).limit(1).toList();
    }
    if (files.isEmpty())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0011");
    return files.getFirst();
  }
}
