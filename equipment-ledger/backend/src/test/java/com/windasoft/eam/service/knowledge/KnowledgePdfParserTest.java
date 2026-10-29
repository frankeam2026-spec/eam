package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class KnowledgePdfParserTest {
  @Test
  void retainsRealPagesAndFlagsScansInsteadOfInventingText() throws Exception {
    try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var first = new PDPage();
      pdf.addPage(first);
      try (var content = new PDPageContentStream(pdf, first)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(40, 700);
        content.showText("P-103 lubrication instructions");
        content.endText();
      }
      pdf.addPage(new PDPage());
      pdf.save(out);
      var pages = new KnowledgePdfParser().parse(out.toByteArray());
      assertEquals(2, pages.size());
      assertEquals(1, pages.getFirst().pageNo());
      assertTrue(pages.getFirst().text().contains("P-103"));
      assertFalse(pages.getFirst().needsOcr());
      assertEquals(2, pages.getLast().pageNo());
      assertTrue(pages.getLast().needsOcr());
      assertTrue(pages.getLast().chunks().isEmpty());
    }
  }

  @Test
  void preservesParagraphsAndUnicodeAcrossLongChunks() {
    String text = "润滑说明\n\n" + "设备𠀀".repeat(900);
    var chunks = KnowledgePdfParser.split(text);
    assertEquals("润滑说明", chunks.getFirst());
    assertEquals(text.replace("\n\n", ""), String.join("", chunks));
    assertTrue(chunks.stream().allMatch(c -> c.codePointCount(0, c.length()) <= 800));
    assertTrue(chunks.stream().noneMatch(c -> Character.isLowSurrogate(c.charAt(0))));
  }

  @Test
  void rejectsInvalidAndOversizedInput() {
    var parser = new KnowledgePdfParser();
    assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[KnowledgePdfParser.MAX_BYTES + 1]));
    assertThrows(java.io.IOException.class, () -> parser.parse(new byte[] {1, 2, 3}));
  }
  @Test void acceptsPdfAboveOld20MbLimit() throws Exception {
    try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){
      pdf.addPage(new PDPage());
      var padding=pdf.getDocument().createCOSStream();
      try(var stream=padding.createOutputStream()){
        byte[] block=new byte[8192];for(int i=0;i<(21*1024*1024)/block.length;i++)stream.write(block);
      }
      pdf.getDocumentCatalog().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.getPDFName("LimitTestPadding"),padding);
      pdf.save(out);byte[] bytes=out.toByteArray();assertTrue(bytes.length>20*1024*1024);
      assertEquals(1,new KnowledgePdfParser().parse(bytes).size());
    }
  }
  @Test void rejectsPdfAbove50MbBeforeParsing(){
    var error=assertThrows(IllegalArgumentException.class,()->new KnowledgePdfParser().parse(new byte[50*1024*1024+1]));
    assertTrue(error.getMessage().contains("50MB"));
  }

}
