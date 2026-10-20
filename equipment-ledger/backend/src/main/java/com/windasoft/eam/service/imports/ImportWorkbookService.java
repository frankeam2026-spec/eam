package com.windasoft.eam.service.imports;

import com.windasoft.eam.dto.imports.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** XLSX protocol only. No permissions, persistence or business writes. */
@Service
public class ImportWorkbookService {
  public static final int MAX_ROWS = 10000, MAX_COLUMNS = 256;

  public ImportWorkbook read(Path file) {
    try {
      // Bound the expanded package before POI allocates XML/string structures.
      try (var zip = new ZipFile(file.toFile())) {
        long expanded = 0;
        int entries = 0;
        var en = zip.entries();
        while (en.hasMoreElements()) {
          var e = en.nextElement();
          if (++entries > 2000 || e.getSize() < 0 || (expanded += e.getSize()) > 128L * 1024 * 1024)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.imports.message0024");
        }
      }
      try (var pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
        var reader = new XSSFReader(pkg);
        SharedStrings strings = reader.getSharedStringsTable();
        StylesTable styles = reader.getStylesTable();
        var it = (XSSFReader.SheetIterator) reader.getSheetsData();
        while (it.hasNext())
          try (var stream = it.next()) {

            var handler = new SheetHandler(strings, styles);
            var parser = XMLHelper.newXMLReader();
            parser.setContentHandler(handler);
            parser.parse(new InputSource(stream));
            return handler.result();
          }
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0025");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.imports.message0026"), e);
    }
  }

  private static final class SheetHandler extends DefaultHandler {
    final SharedStrings strings;
    final StylesTable styles;
    final Map<Integer, String> keys = new TreeMap<>(), labels = new TreeMap<>();
    final List<ImportWorkbook.InputRow> rows = new ArrayList<>();
    Map<Integer, String> values;
    Set<Integer> formulas;
    int row, col, style, cells, version = -1;
    String type = "";
    boolean formula, capture;
    StringBuilder buffer = new StringBuilder();
    String value = "";

    SheetHandler(SharedStrings strings, StylesTable styles) {
      this.strings = strings;
      this.styles = styles;
    }

    @Override
    public void startElement(String u, String l, String q, Attributes a) {
      if (q.equals("row")) {
        row = Integer.parseInt(a.getValue("r"));
        values = new TreeMap<>();
        formulas = new HashSet<>();
      }
      if (q.equals("c")) {
        if (++cells > 2000000)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.imports.message0027");
        String ref = a.getValue("r");
        col = 0;
        for (char c : ref.toCharArray()) {
          if (!Character.isLetter(c)) break;
          col = col * 26 + c - 'A' + 1;
        }
        col--;
        if (col >= MAX_COLUMNS)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.imports.message0028");
        type = Objects.toString(a.getValue("t"), "");
        style = a.getValue("s") == null ? 0 : Integer.parseInt(a.getValue("s"));
        formula = false;
        value = "";
      }
      if (q.equals("f")) formula = true;
      if (q.equals("v") || q.equals("t")) {
        capture = true;
        buffer.setLength(0);
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
      if (capture) buffer.append(ch, start, length);
      if (buffer.length() > 32767)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0029");
    }

    @Override
    public void endElement(String u, String l, String q) {
      if (q.equals("v") || q.equals("t")) {
        capture = false;
        value += buffer.toString();
      }
      if (q.equals("c")) {
        String text = value;
        if (type.equals("s") && !text.isBlank())
          text = strings.getItemAt(Integer.parseInt(text)).getString();
        else if (!type.equals("inlineStr")
            && !type.equals("str")
            && !type.equals("e")
            && !text.isBlank())
          try {
            double n = Double.parseDouble(text);
            var st = styles.getStyleAt(style);
            if (DateUtil.isADateFormat(st.getDataFormat(), st.getDataFormatString())
                && DateUtil.isValidExcelDate(n))
              text = DateUtil.getLocalDateTime(n).toLocalDate().toString();
            else
              text =
                  new DataFormatter(Locale.CHINA)
                      .formatRawCellContents(n, st.getDataFormat(), st.getDataFormatString());
          } catch (NumberFormatException ignored) {
          }
        values.put(col, text.trim());
        if (formula) formulas.add(col);
      }
      if (q.equals("row")) {
        if (row == 1) {
          keys.putAll(values);
          String head = keys.getOrDefault(0, "");
          if (!head.startsWith("eam-equipment-v1|"))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.imports.message0030");
          String[] bits = head.split("\\|", 3);
          version = Integer.parseInt(bits[1]);
          keys.put(0, bits[2]);
        } else if (row == 2) labels.putAll(values);
        else if (values.values().stream().anyMatch(v -> !v.isBlank()) || !formulas.isEmpty()) {
          var vals = new LinkedHashMap<String, String>();
          var fs = new HashSet<String>();
          for (var entry : values.entrySet()) {
            String key = keys.get(entry.getKey());
            if (key == null || key.startsWith("__")) continue;
            vals.put(key, entry.getValue());
            if (formulas.contains(entry.getKey())) fs.add(key);
          }
          if (vals.values().stream().anyMatch(v -> !v.isBlank()) || !fs.isEmpty())
            rows.add(new ImportWorkbook.InputRow(row, vals, fs));
          if (rows.size() > MAX_ROWS)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.imports.message0031");
        }
      }
    }

    ImportWorkbook result() {
      if (version < 0 || keys.isEmpty())
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0032");
      var seen = new HashSet<String>();
      var cols = new ArrayList<ImportColumn>();
      for (var e : keys.entrySet()) {
        if (e.getValue().startsWith("__")) continue;
        if (!seen.add(e.getValue()))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.imports.message0033");
        cols.add(
            new ImportColumn(
                e.getValue(), labels.getOrDefault(e.getKey(), e.getValue()), false, ""));
      }
      if (rows.isEmpty())
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0034");
      return new ImportWorkbook(version, cols, rows);
    }
  }

  public void write(
      OutputStream out,
      int version,
      List<ImportColumn> columns,
      Map<String, List<String>> choices,
      List<ImportWorkbook.InputRow> rows,
      List<ImportIssue> issues)
      throws IOException {
    try (var wb = new SXSSFWorkbook(100)) {
      wb.setCompressTempFiles(true);
      var data =
          wb.createSheet(com.windasoft.eam.common.MessageContext.text("output.import.text0009"));
      data.createFreezePane(0, 2);
      var text = wb.createCellStyle();
      text.setDataFormat(wb.createDataFormat().getFormat("@"));
      var head = wb.createCellStyle();
      head.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
      head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      var font = wb.createFont();
      font.setBold(true);
      head.setFont(font);
      var bad = wb.createCellStyle();
      bad.cloneStyleFrom(text);
      bad.setFillForegroundColor(IndexedColors.ROSE.getIndex());
      bad.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      var ids = data.createRow(0);
      ids.setZeroHeight(true);
      var labels = data.createRow(1);
      for (int i = 0; i < columns.size(); i++) {
        var c = columns.get(i);
        ids.createCell(i)
            .setCellValue((i == 0 ? "eam-equipment-v1|" + version + "|" : "") + c.key());
        var cell = labels.createCell(i);
        cell.setCellValue(c.label() + (c.required() ? " *" : ""));
        cell.setCellStyle(head);
        data.setColumnWidth(
            i,
            (c.key().equals("department") || c.key().equals("functionLocation") ? 38 : 22) * 256);
        data.setDefaultColumnStyle(i, text);
      }
      var grouped = new HashMap<Integer, List<ImportIssue>>();
      issues.forEach(e -> grouped.computeIfAbsent(e.row(), x -> new ArrayList<>()).add(e));
      if (!issues.isEmpty()) {
        ids.createCell(columns.size()).setCellValue("__errors");
        ids.createCell(columns.size() + 1).setCellValue("__suggestions");
        labels
            .createCell(columns.size())
            .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0008"));
        labels
            .createCell(columns.size() + 1)
            .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0007"));
        data.setColumnWidth(columns.size(), 60 * 256);
        data.setColumnWidth(columns.size() + 1, 60 * 256);
      }
      for (var r : rows) {
        var row = data.createRow(r.row() - 1);
        var errors = grouped.getOrDefault(r.row(), List.of());
        for (int i = 0; i < columns.size(); i++) {
          String key = columns.get(i).key();
          var cell = row.createCell(i);
          cell.setCellValue(r.values().getOrDefault(key, ""));
          if (errors.stream().anyMatch(e -> e.field().equals(key))) {
            cell.setCellStyle(bad);
            var drawing = data.createDrawingPatriarch();
            var anchor = wb.getCreationHelper().createClientAnchor();
            anchor.setCol1(i);
            anchor.setCol2(i + 3);
            anchor.setRow1(r.row() - 1);
            anchor.setRow2(r.row() + 3);
            var comment = drawing.createCellComment(anchor);
            comment.setAuthor(com.windasoft.eam.common.MessageContext.text("output.import.check"));
            comment.setString(
                wb.getCreationHelper()
                    .createRichTextString(
                        String.join(
                            "\n",
                            errors.stream()
                                .filter(e -> e.field().equals(key))
                                .map(e -> e.reason() + "；" + e.suggestion())
                                .toList())));
            cell.setCellComment(comment);
          }
        }
        if (!issues.isEmpty()) {
          row.createCell(columns.size())
              .setCellValue(
                  String.join(
                      "；", errors.stream().map(e -> e.label() + "：" + e.reason()).toList()));
          row.createCell(columns.size() + 1)
              .setCellValue(
                  String.join(
                      "；", errors.stream().map(ImportIssue::suggestion).distinct().toList()));
        }
      }
      var help =
          wb.createSheet(com.windasoft.eam.common.MessageContext.text("ui.equipment.text0770"));
      help.setColumnWidth(0, 25 * 256);
      help.setColumnWidth(1, 90 * 256);
      help.createRow(0)
          .createCell(0)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0006"));
      int h = 1;
      for (var c : columns) {
        var r = help.createRow(h++);
        r.createCell(0).setCellValue(c.label());
        r.createCell(1)
            .setCellValue(
                (c.required()
                        ? com.windasoft.eam.common.MessageContext.text("output.import.required")
                        : com.windasoft.eam.common.MessageContext.text("output.import.optional"))
                    + c.instruction());
      }
      var sample = help.createRow(h++);
      sample
          .createCell(0)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0005"));
      sample
          .createCell(1)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0004"));
      var opts =
          wb.createSheet(com.windasoft.eam.common.MessageContext.text("output.import.text0003"));
      opts.createRow(0)
          .createCell(0)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("ui.equipment.text0943"));
      opts.getRow(0)
          .createCell(1)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0002"));
      opts.getRow(0)
          .createCell(2)
          .setCellValue(com.windasoft.eam.common.MessageContext.text("output.import.text0001"));
      opts.setColumnWidth(2, 65 * 256);
      opts.setColumnWidth(0, 25 * 256);
      opts.setColumnWidth(1, 90 * 256);
      int n = 1, seq = 0;
      for (var c : columns) {
        var vals = choices.getOrDefault(c.key(), List.of());
        if (vals.isEmpty()) continue;
        int start = n;
        for (var value : vals) {
          var r = opts.createRow(n++);
          r.createCell(0).setCellValue(c.label());
          r.createCell(1).setCellValue(value);
          if (c.key().equals("functionLocation"))
            r.createCell(2)
                .setCellValue(
                    String.join(
                        "；", choices.getOrDefault("__locationCompany:" + value, List.of())));
        }
        if (!c.key().equals("owner") && !c.key().startsWith("custom:")) {
          var name = wb.createName();
          String key = "_eam_options_" + (seq++);
          name.setNameName(key);
          name.setRefersToFormula(
              "'" + opts.getSheetName().replace("'", "''") + "'!$B$" + (start + 1) + ":$B$" + n);
          var helper = data.getDataValidationHelper();
          var validation =
              helper.createValidation(
                  helper.createFormulaListConstraint(key),
                  new CellRangeAddressList(2, 10001, columns.indexOf(c), columns.indexOf(c)));
          validation.setShowErrorBox(true);
          validation.createErrorBox(
              com.windasoft.eam.common.MessageContext.text("output.import.valid"),
              com.windasoft.eam.common.MessageContext.text("output.import.choices"));
          data.addValidationData(validation);
        }
      }
      wb.write(out);
    }
  }
}
