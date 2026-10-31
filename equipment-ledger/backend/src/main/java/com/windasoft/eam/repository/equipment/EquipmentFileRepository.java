package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentFileRepository {
  private final EquipmentFileMapper fileMapper;
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final String directory;

  public EquipmentFileRepository(
      EquipmentFileMapper fileMapper,
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.fileMapper = fileMapper;
    this.equipment = equipment;
    this.media = media;
    this.directory = directory;
  }

  public List<Map<String, Object>> file(long tenant, String id) {
    var record = fileMapper.selectOne(
        new LambdaQueryWrapper<EquipmentFile>()
            .eq(EquipmentFile::getTenantId, tenant)
            .eq(EquipmentFile::getId, id));
    if (record == null) return List.of();
    var row = new LinkedHashMap<String, Object>();
    row.put("id", record.getId());
    row.put("tenant_id", record.getTenantId());
    row.put("user_id", record.getUserId());
    row.put("name", record.getName());
    row.put("extension", record.getExtension());
    row.put("content_type", record.getContentType());
    row.put("size", record.getSize());
    return List.of(row);
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

  public Path store(InputStream input, String id, String extension) throws IOException {
    Path root = Path.of(directory, "equipment").toAbsolutePath().normalize();
    Files.createDirectories(root);
    Path path = root.resolve(id + extension);
    Files.copy(input, path);
    return path;
  }

  public Path contentPath(String id, String extension) {
    return Path.of(directory, "equipment", id + extension).toAbsolutePath().normalize();
  }

  public void deleteContent(Path path) throws IOException {
    Files.deleteIfExists(path);
  }
}
