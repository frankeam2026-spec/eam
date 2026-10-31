package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EquipmentFileStorageTest {
  @TempDir Path temp;

  @Test
  void storesAndDeletesFileWithoutDatabaseAccess() throws Exception {
    var storage = new EquipmentFileStorage(temp.toString());
    byte[] body = {1, 2, 3, 4};
    Path saved = storage.store(new ByteArrayInputStream(body), "test", ".pdf");
    assertTrue(Files.isRegularFile(saved));
    assertArrayEquals(body, Files.readAllBytes(saved));
    assertTrue(saved.equals(storage.contentPath("test", ".pdf")));
    storage.deleteContent(saved);
    assertFalse(Files.exists(saved));
  }
}
