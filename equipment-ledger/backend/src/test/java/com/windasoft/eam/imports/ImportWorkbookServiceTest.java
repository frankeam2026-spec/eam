package com.windasoft.eam.imports;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.dto.imports.*;
import com.windasoft.eam.service.imports.ImportWorkbookService;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ImportWorkbookServiceTest {
  @TempDir Path temp;
  final ImportWorkbookService service = new ImportWorkbookService();

  @Test
  void roundTripKeepsIdentityRowNumbersAndLeadingZeroes() throws Exception {
    var file = temp.resolve("report.xlsx");
    var cols =
        List.of(
            new ImportColumn("code", "设备编号", false, ""),
            new ImportColumn("name", "设备名称", true, ""));
    try (var out = Files.newOutputStream(file)) {
      service.write(
          out,
          7,
          cols,
          Map.of(),
          List.of(new ImportWorkbook.InputRow(8, Map.of("code", "00012", "name", "空压机"), Set.of())),
          List.of(new ImportIssue(8, "code", "设备编号", "00012", "编号已存在", "更换编号")));
    }
    var data = service.read(file);
    assertEquals(7, data.schemaVersion());
    assertEquals(8, data.rows().getFirst().row());
    assertEquals("00012", data.rows().getFirst().values().get("code"));
    assertEquals(2, data.columns().size());
  }

  @Test
  void emptyTemplateAndCorruptPackageAreExplicit() throws Exception {
    var empty = temp.resolve("empty.xlsx");
    try (var out = Files.newOutputStream(empty)) {
      service.write(
          out,
          0,
          List.of(new ImportColumn("code", "设备编号", false, "")),
          Map.of(),
          List.of(),
          List.of());
    }
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> service.read(empty))
            .getMessage()
            .contains("没有设备数据"));
    var bad = temp.resolve("bad.xlsx");
    Files.writeString(bad, "not an excel workbook");
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> service.read(bad))
            .getMessage()
            .contains("无法读取"));
  }

  @Test
  void formulaCellsAreReportedWithoutEvaluation() throws Exception {
    var file = temp.resolve("formula.xlsx");
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
      var sheet = wb.createSheet("设备数据");
      sheet.createRow(0).createCell(0).setCellValue("eam-equipment-v1|0|code");
      sheet.createRow(1).createCell(0).setCellValue("编号");
      sheet.createRow(2).createCell(0).setCellFormula("1+2");
      try (var out = Files.newOutputStream(file)) {
        wb.write(out);
      }
    }
    var row = service.read(file).rows().getFirst();
    assertTrue(row.formulas().contains("code"));
    assertNotEquals("3", row.values().get("code"));
  }
}
