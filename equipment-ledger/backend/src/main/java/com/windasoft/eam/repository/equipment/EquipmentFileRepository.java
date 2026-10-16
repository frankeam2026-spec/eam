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
  private final String directory;

  public EquipmentFileRepository(
      JdbcTemplate db,
      EquipmentMapper equipment,
      @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.db = db;
    this.equipment = equipment;
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
            .and(
                w ->
                    w.like(Equipment::getDocumentsJson, id).or().like(Equipment::getImagesJson, id).or().eq(Equipment::getImageFileId, id));
    scope.accept(q);
    return equipment.selectList(q);
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
