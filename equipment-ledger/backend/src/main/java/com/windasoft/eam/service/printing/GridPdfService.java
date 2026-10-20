package com.windasoft.eam.service.printing;

import com.fasterxml.jackson.databind.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GridPdfService {
  private final String fontPath;
  private static final float MM = 72f / 25.4f;
  private static final Pattern FIELD = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

  public GridPdfService(@Value("${eam.print.font-path:}") String fontPath) {
    this.fontPath = fontPath;
  }

  public byte[] render(
      double width,
      double height,
      JsonNode layout,
      JsonNode legacy,
      List<Map<String, String>> data,
      int copies)
      throws IOException {
    return render(
        width, height, layout, legacy, data, copies, Collections.nCopies(data.size(), List.of()));
  }

  public byte[] render(
      double width,
      double height,
      JsonNode layout,
      JsonNode legacy,
      List<Map<String, String>> data,
      int copies,
      List<List<Map<String, String>>> details)
      throws IOException {
    if (fontPath.isBlank() || !new File(fontPath).isFile())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.printing.message0010");
    try (var doc = new PDDocument();
        var input = new FileInputStream(fontPath);
        var out = new ByteArrayOutputStream()) {
      var font = PDType0Font.load(doc, input, true);
      if (layout != null && !layout.isNull()) {
        List<JsonNode> layouts = new ArrayList<>();
        List<List<List<Integer>>> pageGroups = new ArrayList<>();
        int total = 0, cellCount = 0;
        for (int i = 0; i < data.size(); i++) {
          var expanded = expand(layout, i < details.size() ? details.get(i) : List.of());
          cellCount += expanded.path("cells").size();
          if (cellCount > 100000)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.printing.message0011");
          layouts.add(expanded);
          var group = segments(expanded, height);
          pageGroups.add(group);
          total += group.size() * copies;
        }
        if (total > 1000)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.printing.message0012");
        int number = 0;
        for (int index = 0; index < data.size(); index++)
          for (int copy = 0; copy < copies; copy++)
            for (var segment : pageGroups.get(index)) {
              var record = data.get(index);
              var recordLayout = layouts.get(index);
              var page = new PDPage(new PDRectangle((float) width * MM, (float) height * MM));
              doc.addPage(page);
              try (var stream = new PDPageContentStream(doc, page)) {
                drawGrid(doc, stream, font, recordLayout, segment, record, (float) height);
                if (layout.path("pageNumbers").asBoolean()) {
                  stream.beginText();
                  stream.setFont(font, 8);
                  stream.newLineAtOffset((float) width * MM / 2 - 20, 5);
                  stream.showText((++number) + " / " + total);
                  stream.endText();
                }
              }
            }
      } else {
        if (data.size() * copies > 1000)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.printing.message0012");
        for (var record : data)
          for (int copy = 0; copy < copies; copy++) {
            var page = new PDPage(new PDRectangle((float) width * MM, (float) height * MM));
            doc.addPage(page);
            try (var stream = new PDPageContentStream(doc, page)) {
              for (var e : legacy) {
                String type = e.path("type").asText();
                String content =
                    e.path("prefix").asText()
                        + (type.equals("text")
                            ? e.path("text").asText()
                            : record.getOrDefault(type, ""));
                drawCell(
                    doc,
                    stream,
                    font,
                    (float) e.path("x").asDouble() * MM,
                    ((float) height
                            - (float) e.path("y").asDouble()
                            - (float) e.path("height").asDouble())
                        * MM,
                    (float) e.path("width").asDouble() * MM,
                    (float) e.path("height").asDouble() * MM,
                    e,
                    content,
                    true);
              }
            }
          }
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private JsonNode expand(JsonNode grid, List<Map<String, String>> records) {
    if (!grid.path("repeat").isObject()) return grid;
    var out = grid.deepCopy();
    int start = grid.path("repeat").path("startRow").asInt(),
        end = grid.path("repeat").path("endRow").asInt(),
        length = end - start + 1,
        count = Math.max(1, records.size());
    if ((long) grid.path("cells").size() * count > 50000)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.printing.message0013");
    var rows = ((com.fasterxml.jackson.databind.node.ObjectNode) out).putArray("rows");
    for (int r = 0; r < start; r++) rows.add(grid.path("rows").get(r));
    for (int n = 0; n < count; n++)
      for (int r = start; r <= end; r++) rows.add(grid.path("rows").get(r));
    for (int r = end + 1; r < grid.path("rows").size(); r++) rows.add(grid.path("rows").get(r));
    var cells = ((com.fasterxml.jackson.databind.node.ObjectNode) out).putArray("cells");
    for (var cell : grid.path("cells")) {
      int r = cell.path("row").asInt();
      if (r >= start && r <= end) {
        for (int n = 0; n < count; n++) {
          var copy = (com.fasterxml.jackson.databind.node.ObjectNode) cell.deepCopy();
          copy.put("row", r + n * length);
          String value = copy.path("content").asText();
          var matcher = FIELD.matcher(value);
          var buffer = new StringBuffer();
          while (matcher.find()) {
            String key = matcher.group(1);
            String replacement =
                key.startsWith("repair_")
                    ? (n < records.size() ? records.get(n).getOrDefault(key, "") : "")
                    : matcher.group();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
          }
          matcher.appendTail(buffer);
          copy.put("content", buffer.toString());
          cells.add(copy);
        }
      } else {
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) cell.deepCopy();
        if (r > end) copy.put("row", r + (count - 1) * length);
        cells.add(copy);
      }
    }
    return out;
  }

  private List<List<Integer>> segments(JsonNode g, double height) {
    int start = g.path("area").isObject() ? g.path("area").path("startRow").asInt() : 0,
        end =
            g.path("area").isObject()
                ? g.path("area").path("endRow").asInt()
                : g.path("rows").size() - 1;
    int headers = start == 0 ? g.path("repeatHeader").asInt() : 0;
    double capacity =
        height
            - g.path("margin").path("top").asDouble()
            - g.path("margin").path("bottom").asDouble()
            - (g.path("pageNumbers").asBoolean() ? 4 : 0);
    List<List<Integer>> result = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    double used = 0;
    for (int r = start; r <= end; ) {
      int blockEnd = r + 1;
      boolean changed;
      do {
        changed = false;
        for (var c : g.path("cells")) {
          int cr = c.path("row").asInt(), ce = cr + c.path("rowSpan").asInt();
          if (cr >= r && cr < blockEnd && ce > blockEnd) {
            blockEnd = ce;
            changed = true;
          }
        }
      } while (changed);
      double blockHeight = 0;
      for (int i = r; i < blockEnd; i++) blockHeight += g.path("rows").get(i).asDouble();
      if (used + blockHeight > capacity + 0.01 && !current.isEmpty()) {
        result.add(current);
        current = new ArrayList<>();
        used = 0;
        for (int h = 0; h < headers; h++) {
          current.add(h);
          used += g.path("rows").get(h).asDouble();
        }
      }
      if (used + blockHeight > capacity + 0.01)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.printing.message0014");
      for (int i = r; i < blockEnd; i++) current.add(i);
      used += blockHeight;
      r = blockEnd;
    }
    if (!current.isEmpty()) result.add(current);
    return result;
  }

  private void drawGrid(
      PDDocument doc,
      PDPageContentStream s,
      PDFont font,
      JsonNode g,
      List<Integer> rowIds,
      Map<String, String> data,
      float paperHeight)
      throws IOException {
    int c0 = g.path("area").isObject() ? g.path("area").path("startCol").asInt() : 0,
        c1 =
            g.path("area").isObject()
                ? g.path("area").path("endCol").asInt()
                : g.path("columns").size() - 1;
    Map<Integer, Float> ys = new HashMap<>();
    float y = (float) g.path("margin").path("top").asDouble();
    for (int r : rowIds) {
      ys.put(r, y);
      y += g.path("rows").get(r).floatValue();
    }
    for (var c : g.path("cells")) {
      int row = c.path("row").asInt(), col = c.path("col").asInt();
      if (!ys.containsKey(row) || col < c0 || col > c1) continue;
      float x = (float) g.path("margin").path("left").asDouble(), w = 0, h = 0;
      for (int i = c0; i < col; i++) x += g.path("columns").get(i).floatValue();
      for (int i = col; i < col + c.path("colSpan").asInt(); i++)
        w += g.path("columns").get(i).floatValue();
      for (int i = row; i < row + c.path("rowSpan").asInt(); i++)
        h += g.path("rows").get(i).floatValue();
      drawCell(
          doc,
          s,
          font,
          x * MM,
          (paperHeight - ys.get(row) - h) * MM,
          w * MM,
          h * MM,
          c,
          resolve(c.path("content").asText(), data),
          false);
    }
  }

  private String resolve(String value, Map<String, String> data) {
    var matcher = FIELD.matcher(value);
    var out = new StringBuffer();
    while (matcher.find())
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(data.getOrDefault(matcher.group(1), "")));
    matcher.appendTail(out);
    return out.toString();
  }

  private void drawCell(
      PDDocument doc,
      PDPageContentStream s,
      PDFont font,
      float x,
      float y,
      float w,
      float h,
      JsonNode c,
      String text,
      boolean legacy)
      throws IOException {
    s.saveGraphicsState();
    s.addRect(x, y, w, h);
    s.clip();
    s.setNonStrokingColor(color(c.path("background").asText("#ffffff")));
    s.addRect(x, y, w, h);
    s.fill();
    s.setNonStrokingColor(color(c.path("color").asText("#000000")));
    s.setStrokingColor(color(c.path("color").asText("#000000")));
    String kind =
        legacy
            ? (c.path("type").asText().equals("image") ? "image" : "text")
            : c.path("kind").asText();
    if (kind.equals("image") || kind.equals("qrcode") || kind.equals("barcode")) {
      BufferedImage image = null;
      if (kind.equals("image")) {
        String uri = c.path("image").asText();
        if (!uri.isBlank()) {
          byte[] bytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
          try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext())
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.printing.message0015");
            var reader = readers.next();
            try {
              reader.setInput(in);
              if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000)
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.printing.message0016");
              image = reader.read(0);
            } finally {
              reader.dispose();
            }
          }
        }
      } else if (!text.isBlank()) {
        try {
          var matrix =
              new MultiFormatWriter()
                  .encode(
                      text,
                      kind.equals("qrcode") ? BarcodeFormat.QR_CODE : BarcodeFormat.CODE_128,
                      kind.equals("qrcode") ? 600 : 1000,
                      kind.equals("qrcode") ? 600 : 250,
                      Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 2));
          image = MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.printing.message0017");
        }
      }
      if (image != null) {
        float ratio = Math.min(w / image.getWidth(), h / image.getHeight()),
            iw = image.getWidth() * ratio,
            ih = image.getHeight() * ratio;
        s.drawImage(
            LosslessFactory.createFromImage(doc, image),
            x + (w - iw) / 2,
            y + (h - ih) / 2,
            iw,
            ih);
      }
    } else {
      text = supported(font, text.replace("\r", "").replace("\t", "    "));
      float size = (float) c.path("fontSize").asDouble(10), padding = legacy ? 0 : 2;
      boolean wrap = c.path("overflow").asText("wrap").equals("wrap");
      var lines = lines(font, text, size, Math.max(1, w - padding * 2), wrap);
      if (c.path("overflow").asText().equals("shrink")) {
        while (size > 6
            && (maxWidth(font, lines, size) > w - padding * 2
                || lines.size() * size * 1.2f > h - padding * 2)) {
          size -= 0.5f;
          lines = lines(font, text, size, w - padding * 2, false);
        }
      }
      float leading = size * 1.2f, total = lines.size() * leading;
      String vertical = c.path("vertical").asText("top");
      float baseline = y + h - padding - size;
      if (vertical.equals("middle")) baseline = y + (h + total) / 2 - size;
      else if (vertical.equals("bottom")) baseline = y + padding + total - size;
      s.setLineWidth(.2f);
      for (var line : lines) {
        float tw = font.getStringWidth(line) / 1000 * size;
        float tx = x + padding;
        String align = c.path("align").asText("left");
        if (align.equals("center")) tx = x + (w - tw) / 2;
        else if (align.equals("right")) tx = x + w - padding - tw;
        s.beginText();
        s.setFont(font, size);
        s.setRenderingMode(
            c.path("bold").asBoolean() ? RenderingMode.FILL_STROKE : RenderingMode.FILL);
        s.setTextMatrix(new Matrix(1, 0, c.path("italic").asBoolean() ? .2f : 0, 1, tx, baseline));
        s.showText(line);
        s.endText();
        if (c.path("underline").asBoolean()) {
          s.moveTo(tx, baseline - 1.5f);
          s.lineTo(tx + tw, baseline - 1.5f);
          s.stroke();
        }
        baseline -= leading;
      }
    }
    s.restoreGraphicsState();
    var sides = c.path("borderSides");
    if (sides.isObject() || c.path("border").asBoolean()) {
      s.saveGraphicsState();
      s.setStrokingColor(Color.decode(c.path("borderColor").asText("#000000")));
      s.setLineWidth((float) c.path("borderWidth").asDouble(.5));
      for (String side : java.util.List.of("top", "right", "bottom", "left")) {
        if (!(sides.isObject() ? sides.path(side).asBoolean() : c.path("border").asBoolean()))
          continue;
        switch (side) {
          case "top" -> {
            s.moveTo(x, y + h);
            s.lineTo(x + w, y + h);
          }
          case "bottom" -> {
            s.moveTo(x, y);
            s.lineTo(x + w, y);
          }
          case "left" -> {
            s.moveTo(x, y);
            s.lineTo(x, y + h);
          }
          case "right" -> {
            s.moveTo(x + w, y);
            s.lineTo(x + w, y + h);
          }
        }
        s.stroke();
      }
      s.restoreGraphicsState();
    }
  }

  private Color color(String value) {
    return Color.decode(value);
  }

  private String supported(PDFont font, String value) throws IOException {
    var b = new StringBuilder();
    for (int cp : value.codePoints().toArray()) {
      String ch = new String(Character.toChars(cp));
      if (cp == 10) {
        b.append(ch);
        continue;
      }
      try {
        font.encode(ch);
        b.append(ch);
      } catch (IllegalArgumentException unsupported) {
        b.append('?');
      }
    }
    return b.toString();
  }

  private List<String> lines(PDFont font, String value, float size, float width, boolean wrap)
      throws IOException {
    List<String> result = new ArrayList<>();
    for (String paragraph : value.split("\n", -1)) {
      if (!wrap) {
        result.add(paragraph);
        continue;
      }
      var line = new StringBuilder();
      for (int cp : paragraph.codePoints().toArray()) {
        String ch = new String(Character.toChars(cp));
        if (!line.isEmpty() && font.getStringWidth(line + ch) / 1000 * size > width) {
          result.add(line.toString());
          line.setLength(0);
        }
        line.append(ch);
      }
      result.add(line.toString());
    }
    return result;
  }

  private float maxWidth(PDFont font, List<String> lines, float size) throws IOException {
    float max = 0;
    for (var l : lines) max = Math.max(max, font.getStringWidth(l) / 1000 * size);
    return max;
  }
}
