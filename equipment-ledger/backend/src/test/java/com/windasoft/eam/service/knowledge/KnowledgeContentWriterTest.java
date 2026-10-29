package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.repository.knowledge.KnowledgeContentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeContentWriterTest {
  @Test
  void cannotReplacePublishedOrAlreadyParsedContent() {
    var repository = mock(KnowledgeContentRepository.class);
    var writer = new KnowledgeContentWriter(repository);
    assertThrows(IllegalStateException.class, () -> writer.commit(1, 2,
        List.of(new KnowledgePdfParser.Page(1, "source", false, List.of("source")))));
    verify(repository, never()).insertPages(anyLong(), anyLong(), anyList());
  }

  @Test
  void checksSourcePageOrderBeforeWriting() {
    var repository = mock(KnowledgeContentRepository.class);
    assertThrows(IllegalArgumentException.class, () -> new KnowledgeContentWriter(repository).commit(1, 2,
        List.of(new KnowledgePdfParser.Page(3, "source", false, List.of("source")))));
    verifyNoInteractions(repository);
  }

  @Test
  void preservesPageAndChunkSourceAndUniqueIdentifiers() {
    var repository = mock(KnowledgeContentRepository.class);
    when(repository.lockParsingVersion(1, 2)).thenReturn(true);
    new KnowledgeContentWriter(repository).commit(1, 2, List.of(
        new KnowledgePdfParser.Page(1, "first\n\nsecond", false, List.of("first", "second")),
        new KnowledgePdfParser.Page(2, "", true, List.of())));
    var captor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(repository).insertPages(eq(1L), eq(2L), captor.capture());
    var first = (KnowledgeContentRepository.Page) captor.getValue().getFirst();
    var second = (KnowledgeContentRepository.Page) captor.getValue().getLast();
    assertEquals(1, first.pageNo());
    assertEquals("second", first.chunks().get(1).text());
    assertNotEquals(first.chunks().getFirst().id(), first.chunks().get(1).id());
    assertNotEquals(first.id(), second.id());
    assertTrue(second.needsOcr());
  }
}
