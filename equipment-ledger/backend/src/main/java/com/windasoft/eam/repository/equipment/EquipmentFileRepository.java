package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentFile;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentFileRepository {
  private final EquipmentFileMapper fileMapper;
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;

  public EquipmentFileRepository(
      EquipmentFileMapper fileMapper,
      EquipmentMapper equipment,
      EquipmentMediaRepository media) {
    this.fileMapper = fileMapper;
    this.equipment = equipment;
    this.media = media;
  }

  public EquipmentFile find(long tenant, String id) {
    return fileMapper.selectOne(
        new LambdaQueryWrapper<EquipmentFile>()
            .eq(EquipmentFile::getTenantId, tenant)
            .eq(EquipmentFile::getId, id));
  }

  public List<Equipment> attachedDevices(
      long tenant, String id, Consumer<LambdaQueryWrapper<Equipment>> scope) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .apply(
                "(image_file_id={0} OR EXISTS(SELECT 1 FROM eam_equipment_image f WHERE"
                    + " f.tenant_id={1} AND f.equipment_id=eam_equipment.id AND f.file_id={0}) OR"
                    + " EXISTS(SELECT 1 FROM eam_equipment_document f WHERE f.tenant_id={1} AND"
                    + " f.equipment_id=eam_equipment.id AND f.file_id={0}))",
                id, tenant);
    scope.accept(q);
    return media.hydrate(equipment.selectList(q));
  }

  public void insert(
      String id,
      long tenant,
      long user,
      String name,
      String extension,
      String contentType,
      long size) {
    var record = new EquipmentFile();
    record.setId(id);
    record.setTenantId(tenant);
    record.setUserId(user);
    record.setName(name);
    record.setExtension(extension);
    record.setContentType(contentType);
    record.setSize(size);
    fileMapper.insert(record);
  }

}
