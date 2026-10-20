package com.windasoft.eam.service.common;

import java.util.Objects;

/** CSV formatting is shared; text must never be rewritten as a date. */
public final class CsvCells {
  private CsvCells() {}

  public static String format(Object value) {
    String text = Objects.toString(value, "");
    if (text.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*"))
      text = text.substring(0, 10) + " " + text.substring(11);
    if (text.stripLeading().matches("(?s)^[=+@-].*")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
