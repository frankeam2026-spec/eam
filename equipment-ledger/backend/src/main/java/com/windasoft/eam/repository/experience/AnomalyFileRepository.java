package com.windasoft.eam.repository.experience;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnomalyFileRepository {
  private final JdbcTemplate db;
  private final Path directory;

  public AnomalyFileRepository(
      JdbcTemplate db, @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.db = db;
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  public Path path(String source, String id, String extension) {
    if (!Set.of("anomalies", "maintenance", "inspection").contains(source))
      throw new IllegalArgumentException("附件来源无效");
    return directory.resolve(source).resolve(id + extension).normalize();
  }

  public void store(String id, String extension, InputStream content) throws IOException {
    var target = path("anomalies", id, extension);
    Files.createDirectories(target.getParent());
    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
  }

  public int insertFile(
      Object id, long tenantId, long userId, Object name, Object ext, Object type, Object value) {
    return db.update(
        "INSERT INTO eam_anomaly_file(id,tenant_id,user_id,name,extension,content_type,size)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        tenantId,
        userId,
        name,
        ext,
        type,
        value);
  }

  public List<Map<String, Object>> findTemporary(long tenantId, long userId, Object fileId) {
    return db.queryForList(
        "SELECT * FROM eam_anomaly_file WHERE tenant_id=? AND user_id=? AND id=?",
        tenantId,
        userId,
        fileId);
  }

  public List<Map<String, Object>> findSource(long tenantId, Object fileId, String table) {
    if (!Set.of("eam_anomaly_file", "eam_maintenance_file", "eam_inspection_file").contains(table))
      throw new IllegalArgumentException("附件来源无效");
    return db.queryForList(
        "SELECT name,extension FROM " + table + " WHERE tenant_id=? AND id=?", tenantId, fileId);
  }
}
