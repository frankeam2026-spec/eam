package com.windasoft.eam.service.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CsvCellsTest {
  @Test
  void preservesTextAndEscapesFormulasAndQuotes() {
    assertEquals("\"TEST MATERIAL\"", CsvCells.format("TEST MATERIAL"));
    assertEquals("\"'=1+1\"", CsvCells.format("=1+1"));
    assertEquals("\"A\"\"B\"", CsvCells.format("A\"B"));
  }

  @Test
  void formatsOnlyActualDateTimeStrings() {
    assertEquals("\"2026-10-17 10:00:00\"", CsvCells.format("2026-10-17T10:00:00"));
    assertEquals("\"TEST-TYPE\"", CsvCells.format("TEST-TYPE"));
  }
}
