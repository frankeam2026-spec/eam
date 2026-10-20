package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentFileRepository {
  private final JdbcTemplate db;
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final String directory;

  public EquipmentFileRepository(
      JdbcTemplate db,
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.db = db;
    this.equipment = equipment;
    this.media = media;
    this.directory = directory;
  }

  public List<Map<String, Object>> file(long tenant, String id) {
    return db.queryForList(
        "SELECT * FROM eam_equipment_file WHERE tenant_id=? AND id=?", tenant, id);
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
    db.update(
        "INSERT INTO eam_equipment_file(id,tenant_id,user_id,name,extension,content_type,size)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        tenant,
        user,
        name,
        extension,
        contentType,
        size);
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
