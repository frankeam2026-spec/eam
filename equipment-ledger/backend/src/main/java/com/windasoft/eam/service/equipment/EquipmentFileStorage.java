package com.windasoft.eam.service.equipment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 设备文件的磁盘读写，不属于数据库 Repository 的职责。 */
@Component
public class EquipmentFileStorage {
  private final Path directory;

  public EquipmentFileStorage(@Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.directory = Path.of(directory, "equipment").toAbsolutePath().normalize();
  }

  public Path store(InputStream input, String id, String extension) throws IOException {
    Files.createDirectories(directory);
    Path path = contentPath(id, extension);
    Files.copy(input, path);
    return path;
  }

  public Path contentPath(String id, String extension) {
    return directory.resolve(id + extension);
  }

  public void deleteContent(Path path) throws IOException {
    Files.deleteIfExists(path);
  }
}
