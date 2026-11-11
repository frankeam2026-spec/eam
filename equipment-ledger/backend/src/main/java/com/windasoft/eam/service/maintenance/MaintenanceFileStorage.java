package com.windasoft.eam.service.maintenance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 保养附件的磁盘适配，沿用 maintenance 子目录；不查询数据库，也不决定业务权限。 */
@Component
public class MaintenanceFileStorage {
  private final Path directory;

  public MaintenanceFileStorage(@Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.directory = Path.of(directory, "maintenance").toAbsolutePath().normalize();
  }

  public Path path(String id, String extension) {
    return directory.resolve(id + extension).normalize();
  }

  public void store(String id, String extension, InputStream content) throws IOException {
    Files.createDirectories(directory);
    Files.copy(content, path(id, extension), StandardCopyOption.REPLACE_EXISTING);
  }

  public void delete(String id, String extension) throws IOException {
    Files.deleteIfExists(path(id, extension));
  }
}
