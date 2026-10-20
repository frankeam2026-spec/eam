package com.windasoft.eam.service.printing;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fixed business sections translated to the existing, shared grid/PDF drawing engine. */
@Component
public class DocumentPrintLayout {
  public static final Map<String, String> HEADERS = new LinkedHashMap<>();
  public static final Map<String, String> COLUMNS = new LinkedHashMap<>();
  public static final Set<String> MONEY = Set.of("unitCost", "amount", "currency");

  static {
    HEADERS.put("warehouseName", "仓库");
    HEADERS.put("sourceKind", "出库类型");
    HEADERS.put("confirmedAt", "出库时间");
    HEADERS.put(
        "recipientName", com.windasoft.eam.common.MessageContext.text("ui.equipment.text0564"));
    HEADERS.put("departmentName", "领用部门");
    HEADERS.put("taskCode", "关联任务");
    HEADERS.put("purpose", "用途");
    HEADERS.put("remark", "备注");
    HEADERS.put("actorName", "操作人");
    COLUMNS.put("sequence", "序号");
    COLUMNS.put("code", "料号");
    COLUMNS.put("name", "物料名称");
    COLUMNS.put("model", "规格型号");
    COLUMNS.put("unit", "单位");
    COLUMNS.put("quantity", "本次数量");
    COLUMNS.put("recipient", com.windasoft.eam.common.MessageContext.text("ui.equipment.text0564"));
    COLUMNS.put("department", "领用部门");
    COLUMNS.put("task", "任务编号");
    COLUMNS.put("requisition", "申请编号");
    COLUMNS.put("batch", "批次");
    COLUMNS.put("bin", "库位");
    COLUMNS.put("unitCost", "实际单价");
    COLUMNS.put("amount", "实际金额");
    COLUMNS.put("currency", "币种");
  }

  private final ObjectMapper json;
  private final GridPdfService pdf;
  private final String fontPath;

  public DocumentPrintLayout(
      ObjectMapper json, GridPdfService pdf, @Value("${eam.print.font-path:}") String path) {
    this.json = json;
    this.pdf = pdf;
    this.fontPath = path;
  }

  public Map<String, String> headerLabels() {
    var labels = new LinkedHashMap<String, String>();
    HEADERS
        .keySet()
        .forEach(
            k ->
                labels.put(
                    k, com.windasoft.eam.common.MessageContext.text("output.print.headers." + k)));
    return labels;
  }

  public Map<String, String> columnLabels() {
    var labels = new LinkedHashMap<String, String>();
    COLUMNS
        .keySet()
        .forEach(
            k ->
                labels.put(
                    k, com.windasoft.eam.common.MessageContext.text("output.print.columns." + k)));
    return labels;
  }

  public JsonNode defaults() {
    var c = json.createObjectNode();
    c.put("title", com.windasoft.eam.common.MessageContext.text("output.print.text0001"));
    c.put("paper", "A4");
    c.put("landscape", false);
    c.put("widthMm", 210);
    c.put("heightMm", 297);
    c.put("fontSize", 10);
    c.put("logo", "");
    c.put("footer", "");
    var h = c.putArray("headers");
    for (String k : List.of("warehouseName", "sourceKind", "confirmedAt", "actorName", "purpose"))
      h.add(k);
    var cols = c.putArray("columns");
    for (String key :
        List.of("sequence", "code", "name", "model", "unit", "quantity", "recipient", "task")) {
      var col = cols.addObject();
      col.put("key", key);
      col.put("label", com.windasoft.eam.common.MessageContext.text("output.print.columns." + key));
      col.put(
          "width",
          key.equals("sequence") || key.equals("unit")
              ? 6
              : key.equals("name") ? 20 : key.equals("task") ? 18 : 14);
    }
    var signatures = c.putArray("signatures");
    signatures.add(com.windasoft.eam.common.MessageContext.text("output.print.text0002"));
    signatures.add(com.windasoft.eam.common.MessageContext.text("ui.equipment.text0564"));
    return c;
  }

  public boolean money(JsonNode c) {
    for (var col : c.path("columns")) if (MONEY.contains(col.path("key").asText())) return true;
    return false;
  }

  private void require(boolean value, String message) {
    if (!value) throw new IllegalArgumentException(message);
  }

  public void validate(JsonNode c) {
    require(
        c.isObject() && c.toString().length() < 800000,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0029"));
    require(
        !c.path("title").asText().isBlank() && c.path("title").asText().length() <= 80,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0030"));
    require(
        Set.of("A4", "A5", "CUSTOM").contains(c.path("paper").asText()),
        com.windasoft.eam.common.MessageContext.text("server.printing.message0031"));
    require(
        c.path("fontSize").isNumber()
            && c.path("fontSize").asDouble() >= 8
            && c.path("fontSize").asDouble() <= 14,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0032"));
    double w = width(c), h = height(c);
    require(
        Double.isFinite(w) && Double.isFinite(h) && w >= 80 && w <= 600 && h >= 100 && h <= 600,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0033"));
    require(
        c.path("headers").isArray() && c.path("headers").size() <= HEADERS.size(),
        com.windasoft.eam.common.MessageContext.text("server.printing.message0034"));
    var seen = new HashSet<String>();
    for (var k : c.path("headers"))
      require(
          HEADERS.containsKey(k.asText()) && seen.add(k.asText()),
          com.windasoft.eam.common.MessageContext.text("server.printing.message0035"));
    var cols = c.path("columns");
    require(
        cols.isArray() && cols.size() >= 3 && cols.size() <= COLUMNS.size(),
        com.windasoft.eam.common.MessageContext.text("server.printing.message0036"));
    seen.clear();
    for (var col : cols) {
      String k = col.path("key").asText();
      require(
          COLUMNS.containsKey(k) && seen.add(k),
          com.windasoft.eam.common.MessageContext.text("server.printing.message0037"));
      require(
          !col.path("label").asText().isBlank() && col.path("label").asText().length() <= 24,
          com.windasoft.eam.common.MessageContext.text("server.printing.message0038"));
      double n = col.path("width").asDouble(Double.NaN);
      require(
          Double.isFinite(n) && n >= 3 && n <= 100,
          com.windasoft.eam.common.MessageContext.text("server.printing.message0039"));
    }
    require(
        seen.containsAll(Set.of("code", "name", "quantity")),
        com.windasoft.eam.common.MessageContext.text("server.printing.message0040"));
    require(
        c.path("signatures").isArray() && c.path("signatures").size() <= 4,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0041"));
    for (var item : c.path("signatures"))
      require(
          !item.asText().isBlank() && item.asText().length() <= 20,
          com.windasoft.eam.common.MessageContext.text("server.printing.message0042"));
    require(
        c.path("footer").asText().length() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0043"));
    String logo = c.path("logo").asText();
    require(
        logo.isEmpty() || logo.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+"),
        com.windasoft.eam.common.MessageContext.text("server.printing.message0044"));
    if (!logo.isEmpty()) {
      byte[] bytes = Base64.getDecoder().decode(logo.substring(logo.indexOf(',') + 1));
      require(
          bytes.length <= 512000,
          com.windasoft.eam.common.MessageContext.text("server.printing.message0045"));
      try (var input =
          javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = javax.imageio.ImageIO.getImageReaders(input);
        require(
            readers.hasNext(),
            com.windasoft.eam.common.MessageContext.text("server.printing.message0046"));
        var reader = readers.next();
        try {
          reader.setInput(input);
          require(
              (long) reader.getWidth(0) * reader.getHeight(0) <= 4000000,
              com.windasoft.eam.common.MessageContext.text("server.printing.message0047"));
          require(
              Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase()),
              com.windasoft.eam.common.MessageContext.text("server.printing.message0048"));
        } finally {
          reader.dispose();
        }
      } catch (IOException e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.printing.message0008");
      }
    }
  }

  public double width(JsonNode c) {
    double w =
        c.path("paper").asText().equals("A4")
            ? 210
            : c.path("paper").asText().equals("A5") ? 148 : c.path("widthMm").asDouble(Double.NaN);
    return c.path("landscape").asBoolean() ? rawHeight(c) : w;
  }

  private double rawHeight(JsonNode c) {
    return c.path("paper").asText().equals("A4")
        ? 297
        : c.path("paper").asText().equals("A5") ? 210 : c.path("heightMm").asDouble(Double.NaN);
  }

  public double height(JsonNode c) {
    return c.path("landscape").asBoolean()
        ? (c.path("paper").asText().equals("A4")
            ? 210
            : c.path("paper").asText().equals("A5") ? 148 : c.path("widthMm").asDouble(Double.NaN))
        : rawHeight(c);
  }

  public byte[] render(JsonNode c, Map<String, Object> document, List<Map<String, String>> lines)
      throws IOException {
    validate(c);
    if (fontPath.isBlank() || !new File(fontPath).isFile())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.printing.message0009");
    try (var metricsDocument = new PDDocument();
        var input = new FileInputStream(fontPath)) {
      var font = PDType0Font.load(metricsDocument, input, true);
      var b = new Builder(c, font);
      String status = String.valueOf(document.getOrDefault("status", ""));
      if (!c.path("logo").asText().isEmpty()) b.image(c.path("logo").asText());
      b.full(String.valueOf(document.getOrDefault("companyName", "")), 12, true, false);
      b.full(c.path("title").asText(), 18, true, false);
      b.full(
          com.windasoft.eam.common.MessageContext.text("output.print.text0004")
              + document.get("code")
              + "    "
              + (status.equals("草稿")
                  ? com.windasoft.eam.common.MessageContext.text("output.print.text0003")
                  : com.windasoft.eam.common.SystemEnumLabels.display("status", status)),
          10,
          true,
          false);
      var entries = new ArrayList<String>();
      for (var field : c.path("headers"))
        entries.add(
            com.windasoft.eam.common.MessageContext.text("output.print.headers." + field.asText())
                + "："
                + (field.asText().equals("sourceKind")
                    ? com.windasoft.eam.common.SystemEnumLabels.display(
                        "outboundType", document.get(field.asText()))
                    : String.valueOf(document.getOrDefault(field.asText(), ""))));
      for (int i = 0; i < entries.size(); i += 2)
        b.full(
            entries.get(i) + (i + 1 < entries.size() ? "     " + entries.get(i + 1) : ""),
            c.path("fontSize").asDouble(),
            false,
            false);
      b.tableRow(null, true);
      b.grid.put("repeatHeader", b.rows.size());
      // Source identity remains visible even when optional columns are removed.
      String previousSource = "";
      boolean multipleSources =
          lines.stream()
                  .map(
                      l ->
                          List.of(
                              l.getOrDefault("requisition", ""),
                              l.getOrDefault("recipient", ""),
                              l.getOrDefault("department", ""),
                              l.getOrDefault("task", "")))
                  .distinct()
                  .limit(2)
                  .count()
              > 1;
      for (var line : lines) {
        if (multipleSources) {
          String source =
              com.windasoft.eam.common.MessageContext.text("output.print.requisition")
                  + line.getOrDefault("requisition", "")
                  + com.windasoft.eam.common.MessageContext.text("output.print.recipient")
                  + line.getOrDefault("recipient", "")
                  + com.windasoft.eam.common.MessageContext.text("output.print.department")
                  + line.getOrDefault("department", "")
                  + com.windasoft.eam.common.MessageContext.text("output.print.task")
                  + line.getOrDefault("task", "");
          if (!source.equals(previousSource)) {
            b.full(source, 9, false, true);
            previousSource = source;
          }
        }
        b.tableRow(line, false);
      }
      if (money(c)) {
        var totals = new TreeMap<String, java.math.BigDecimal>();
        for (var line : lines)
          totals.merge(
              line.getOrDefault(
                  "currency",
                  com.windasoft.eam.common.MessageContext.text("output.print.unknownCurrency")),
              new java.math.BigDecimal(line.getOrDefault("amount", "0")),
              java.math.BigDecimal::add);
        for (var entry : totals.entrySet())
          b.full(
              com.windasoft.eam.common.MessageContext.text("output.print.total")
                  + entry.getKey()
                  + com.windasoft.eam.common.MessageContext.text("output.print.totalEnd")
                  + entry.getValue().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
              11,
              true,
              false);
      }
      var signatures = new ArrayList<String>();
      for (var item : c.path("signatures")) signatures.add(item.asText() + "：________________");
      if (!signatures.isEmpty()) b.full(String.join("     ", signatures), 10, false, false);
      if (!c.path("footer").asText().isBlank()) b.full(c.path("footer").asText(), 9, false, false);
      return pdf.render(width(c), height(c), b.grid, json.createArrayNode(), List.of(b.values), 1);
    }
  }

  private final class Builder {
    final ObjectNode grid = json.createObjectNode();
    final ArrayNode rows, cells;
    final Map<String, String> values = new LinkedHashMap<>();
    final List<Double> widths = new ArrayList<>();
    final JsonNode config;
    final PDFont font;
    final double usable;
    int key = 0;

    Builder(JsonNode config, PDFont font) {
      this.config = config;
      this.font = font;
      usable = width(config) - 20;
      grid.put("version", 1);
      grid.put("pageNumbers", true);
      grid.putNull("area");
      grid.put("repeatHeader", 0);
      grid.putObject("margin").put("left", 10).put("right", 10).put("top", 10).put("bottom", 10);
      double total = 0;
      for (var col : config.path("columns")) total += col.path("width").asDouble();
      var columns = grid.putArray("columns");
      for (var col : config.path("columns")) {
        double w = usable * col.path("width").asDouble() / total;
        require(
            w * 72 / 25.4 - 6 >= config.path("fontSize").asDouble(),
            com.windasoft.eam.common.MessageContext.text("server.printing.message0049"));
        widths.add(w);
        columns.add(w);
      }
      rows = grid.putArray("rows");
      cells = grid.putArray("cells");
    }

    double textHeight(String text, double size, double width) throws IOException {
      float available = (float) ((width * 72 / 25.4) - 6);
      int count = 1;
      float used = 0;
      for (int offset = 0; offset < text.length(); ) {
        int cp = text.codePointAt(offset);
        offset += Character.charCount(cp);
        if (cp == '\n') {
          count++;
          used = 0;
          continue;
        }
        if (Character.isISOControl(cp)) continue;
        String ch = new String(Character.toChars(cp));
        float w = font.getStringWidth(ch) / 1000 * (float) size;
        if (used + w > available && used > 0) {
          count++;
          used = 0;
        }
        used += w;
      }
      return Math.max(8, (count * size * 1.2 + 8) * 25.4 / 72);
    }

    ObjectNode cell(
        int row, int col, int span, String text, double size, boolean bold, boolean border) {
      String name = "field" + (key++);
      values.put(name, text.replaceAll("[\\p{Cntrl}&&[^\\n]]", ""));
      var cell = cells.addObject();
      cell.put("row", row)
          .put("col", col)
          .put("rowSpan", 1)
          .put("colSpan", span)
          .put("kind", "text")
          .put("content", "{" + name + "}")
          .put("image", "")
          .put("fontSize", size)
          .put("bold", bold)
          .put("italic", false)
          .put("underline", false)
          .put("align", "left")
          .put("vertical", "middle")
          .put("color", "#182438")
          .put("background", border && bold ? "#f2f4f7" : "#ffffff")
          .put("border", border)
          .put("overflow", "wrap");
      return cell;
    }

    void full(String text, double size, boolean bold, boolean border) throws IOException {
      int row = rows.size();
      rows.add(textHeight(text, size, usable) + 1);
      var c = cell(row, 0, widths.size(), text, size, bold, border);
      if (bold) c.put("align", "center");
    }

    void image(String logo) {
      int row = rows.size();
      rows.add(12);
      cell(row, 0, widths.size(), "", 10, false, false).put("kind", "image").put("image", logo);
    }

    void tableRow(Map<String, String> data, boolean heading) throws IOException {
      int row = rows.size(), col = 0;
      double size = config.path("fontSize").asDouble(), h = 8;
      for (var def : config.path("columns")) {
        String text =
            heading ? def.path("label").asText() : data.getOrDefault(def.path("key").asText(), "");
        h = Math.max(h, textHeight(text, size, widths.get(col)));
        var c = cell(row, col++, 1, text, size, heading, true);
        if (Set.of("quantity", "unitCost", "amount").contains(def.path("key").asText()))
          c.put("align", "right");
      }
      rows.add(h);
    }
  }
}
