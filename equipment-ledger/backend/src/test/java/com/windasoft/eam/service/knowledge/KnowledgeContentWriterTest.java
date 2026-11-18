package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.knowledge.KnowledgeChunk;
import com.windasoft.eam.entity.knowledge.KnowledgePage;
import com.windasoft.eam.repository.knowledge.KnowledgeChunkRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeContentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgePageRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeContentWriterTest {
  final KnowledgeContentRepository locks = mock(KnowledgeContentRepository.class);
  final KnowledgePageRepository pages = mock(KnowledgePageRepository.class);
  final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
  final KnowledgeVersionRepository versions = mock(KnowledgeVersionRepository.class);

  KnowledgeContentWriter writer() {
    return new KnowledgeContentWriter(locks, pages, chunks, versions);
  }

  @Test
  void cannotReplacePublishedOrAlreadyParsedContent() {
    assertThrows(
        IllegalStateException.class,
        () ->
            writer()
                .commit(
                    1,
                    2,
                    List.of(new KnowledgePdfParser.Page(1, "source", false, List.of("source")))));
    verifyNoInteractions(pages, chunks, versions);
  }

  @Test
  void checksSourcePageOrderBeforeWriting() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer()
                .commit(
                    1,
                    2,
                    List.of(new KnowledgePdfParser.Page(3, "source", false, List.of("source")))));
    verifyNoInteractions(locks, pages, chunks, versions);
  }

  @Test
  void preservesPageAndChunkSourceAndUniqueIdentifiers() {
    when(locks.lockParsingVersion(1, 2)).thenReturn(true);
    writer()
        .commit(
            1,
            2,
            List.of(
                new KnowledgePdfParser.Page(
                    1, "first\n\nsecond", false, List.of("first", "second")),
                new KnowledgePdfParser.Page(2, "", true, List.of())));
    var pageCaptor = ArgumentCaptor.forClass(List.class);
    var chunkCaptor = ArgumentCaptor.forClass(List.class);
    verify(pages).insert(pageCaptor.capture());
    verify(chunks).insert(chunkCaptor.capture());
    var first = (KnowledgePage) pageCaptor.getValue().getFirst();
    var second = (KnowledgePage) pageCaptor.getValue().getLast();
    var c1 = (KnowledgeChunk) chunkCaptor.getValue().getFirst();
    var c2 = (KnowledgeChunk) chunkCaptor.getValue().getLast();
    assertEquals(1, first.getPageNo());
    assertEquals("second", c2.getTextContent());
    assertNotEquals(c1.getId(), c2.getId());
    assertNotEquals(first.getId(), second.getId());
    assertTrue(second.getNeedsOcr());
    assertEquals(first.getId(), c1.getPageId());
    assertEquals(first.getId(), c2.getPageId());
    verify(versions).finishParsed(1, 2, true);
  }
}
