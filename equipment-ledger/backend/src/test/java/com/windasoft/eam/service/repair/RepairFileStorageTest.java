package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class RepairFileStorageTest {
  @TempDir Path directory;

  @Test
  void storesAndFindsFileWithinConfiguredDirectory() throws Exception {
    var storage = new RepairFileStorage(directory.toString());
    String id = "01234567-89ab-4cde-8012-3456789abcde";
    storage.store(
        new ByteArrayInputStream("测试附件".getBytes(StandardCharsets.UTF_8)), id, ".txt");

    assertEquals(id + ".txt", storage.attachmentPath(id).getFileName().toString());
    assertEquals("测试附件", Files.readString(storage.attachmentPath(id)));
  }

  @Test
  void rejectsPathTraversalOnWrite() {
    var storage = new RepairFileStorage(directory.toString());
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(new ByteArrayInputStream(new byte[0]), "../outside", ".txt"));
  }

  @Test
  void rejectsMissingAttachment() {
    var storage = new RepairFileStorage(directory.toString());
    assertThrows(IllegalArgumentException.class,
        () -> storage.attachmentPath("01234567-89ab-4cde-8012-3456789abcde"));
  }
}
