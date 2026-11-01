package com.windasoft.eam.service.repair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 维修附件的本地文件存储适配层；不判断附件归属或当前用户的业务权限。 */
@Component
public class RepairFileStorage {
  private final Path root;

  public RepairFileStorage(@Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
  }

  /** 将已由业务层校验类型的附件保存到存储目录，拒绝越过根目录的路径。 */
  public void store(InputStream input, String id, String extension) throws IOException {
    Files.createDirectories(root);
    Path target = root.resolve(id + extension).normalize();
    if (!target.getParent().equals(root))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0010");
    Files.copy(input, target);
  }

  /** 通过受控的附件 ID 定位文件；调用方必须先完成 ID 校验和关联权限检查。 */
  public Path attachmentPath(String id) throws IOException {
    if (!Files.isDirectory(root))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0011");
    // 附件写入使用 UUID 加扩展名；只匹配完整的“ID.”前缀，避免误取同前缀文件。
    List<Path> matches;
    try (var stream = Files.list(root)) {
      matches = stream.filter(p -> p.getFileName().toString().startsWith(id + ".")).limit(1).toList();
    }
    if (matches.isEmpty())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0011");
    return matches.getFirst();
  }
}
