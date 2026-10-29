package com.windasoft.eam.service.knowledge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts source-grounded text only. OCR and figure association must not be guessed. */
@Component
public class KnowledgePdfParser {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private KnowledgeOcr ocr;
  public static final String VERSION = "pdfbox-3.0.5-paragraph-optional-ocr-v2";
  static final int MAX_BYTES = KnowledgeDocumentService.MAX_PDF_BYTES;
  static final int MAX_PAGES = 500;
  static final int MAX_CHARACTERS = 2_000_000;
  static final int CHUNK_CODE_POINTS = 800;

  public record Page(int pageNo, String text, boolean needsOcr, List<String> chunks) {}

  public List<Page> parse(byte[] bytes) throws IOException {
    return parse(bytes,()->{});
  }
  public List<Page> parse(byte[] bytes,Runnable heartbeat) throws IOException {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
      throw new IllegalArgumentException("PDF文件须为1字节至50MB");
    try (var pdf = Loader.loadPDF(bytes)) {
      if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent())
        throw new IllegalArgumentException("请上传允许提取内容且未加密的PDF");
      if (pdf.getNumberOfPages() == 0 || pdf.getNumberOfPages() > MAX_PAGES)
        throw new IllegalArgumentException("PDF须包含1至500页");
      var result = new ArrayList<Page>();
      int total = 0;
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      stripper.setParagraphEnd("\n\n");
      for (int number = 1; number <= pdf.getNumberOfPages(); number++) {
        heartbeat.run();
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        String text = stripper.getText(pdf).replace("\r\n", "\n").replace('\r', '\n').strip();
        if(text.isBlank() && ocr!=null && ocr.enabled()){
          var box=pdf.getPage(number-1).getCropBox();
          if(box.getWidth()<=0||box.getHeight()<=0||box.getWidth()*box.getHeight()*9>20_000_000)throw new IllegalArgumentException("扫描页面尺寸超过OCR限制");
          try{text=ocr.extract(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(number-1,216));}
          catch(Exception error){throw new IOException("扫描页OCR失败，请检查本地OCR配置",error);}
        }
        total += text.length();
        if (total > MAX_CHARACTERS) throw new IllegalArgumentException("PDF提取文字超过200万字符限制");
        // Blank pages stay explicit: the caller must use OCR/review rather than publish empty knowledge.
        result.add(new Page(number, text, text.isBlank(), split(text)));
      }
      return List.copyOf(result);
    }
  }

  static List<String> split(String text) {
    var chunks = new ArrayList<String>();
    var current = new StringBuilder();
    for (String raw : text.split("\\n\\s*\\n")) {
      String paragraph = raw.strip();
      if (paragraph.isEmpty()) continue;
      if (!current.isEmpty()
          && current.codePointCount(0, current.length()) + 2
              + paragraph.codePointCount(0, paragraph.length()) > CHUNK_CODE_POINTS) {
        chunks.add(current.toString());
        current.setLength(0);
      }
      // Split exceptionally long paragraphs without cutting surrogate pairs.
      while (paragraph.codePointCount(0, paragraph.length()) > CHUNK_CODE_POINTS) {
        int end = paragraph.offsetByCodePoints(0, CHUNK_CODE_POINTS);
        chunks.add(paragraph.substring(0, end));
        paragraph = paragraph.substring(end);
      }
      if (!paragraph.isBlank()) {
        if (!current.isEmpty()) current.append("\n\n");
        current.append(paragraph);
      }
    }
    if (!current.isEmpty()) chunks.add(current.toString());
    return List.copyOf(chunks);
  }
}
