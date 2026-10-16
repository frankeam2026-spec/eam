package com.windasoft.eam.repository.maintenance;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceFileRepository {
  private final JdbcTemplate db;
  private final Path directory;

  public MaintenanceFileRepository(
      JdbcTemplate db, @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.db = db;
    this.directory = Path.of(directory, "maintenance").toAbsolutePath().normalize();
  }

  public Path path(String id, String extension) {
    return directory.resolve(id + extension).normalize();
  }

  public void store(String id, String extension, InputStream content) throws IOException {
    Files.createDirectories(directory);
    Files.copy(content, path(id, extension), StandardCopyOption.REPLACE_EXISTING);
  }

  public List<Map<String, Object>> find(long tenant, String id) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_file WHERE tenant_id=? AND id=?", tenant, id);
  }

  public void insert(
      String id, long tenant, long user, String name, String extension, String type, long size) {
    db.update(
        "INSERT INTO eam_maintenance_file(id,tenant_id,user_id,name,extension,content_type,size)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        tenant,
        user,
        name,
        extension,
        type,
        size);
  }
}
