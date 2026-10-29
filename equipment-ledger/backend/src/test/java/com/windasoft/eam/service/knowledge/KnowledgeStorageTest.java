package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.windasoft.eam.repository.knowledge.KnowledgeContentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class KnowledgeStorageTest {
  @Test
  void migrationAndAtomicContentWriteAndTenantIsolation() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V108__knowledge_documents.sql"))
        .execute(source);
    var db = new JdbcTemplate(source);
    db.update("INSERT INTO eam_knowledge_document(id,tenant_id,company_id,title,document_type,created_by) VALUES(1,10,20,'manual','MANUAL',30)");
    db.update("INSERT INTO eam_knowledge_version(id,tenant_id,document_id,version_no,file_key,sha256,parser_version,parse_status) VALUES(2,10,1,1,'file',?,'test','RUNNING')", "a".repeat(64));
    var repo = new KnowledgeContentRepository(db);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    var page = new KnowledgeContentRepository.Page(3, 1, "source", false,
        List.of(new KnowledgeContentRepository.Chunk(4, 1, "source")));
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
      assertTrue(repo.lockParsingVersion(10, 2));
      repo.insertPages(10, 2, List.of(page));
      throw new IllegalStateException("simulate failure after writes");
    }));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_page", Integer.class));
    assertEquals("RUNNING", db.queryForObject("SELECT parse_status FROM eam_knowledge_version", String.class));
    tx.executeWithoutResult(status -> {
      assertFalse(repo.lockParsingVersion(11, 2));
      assertTrue(repo.lockParsingVersion(10, 2));
      repo.insertPages(10, 2, List.of(page));
    });
    assertEquals("READY", db.queryForObject("SELECT parse_status FROM eam_knowledge_version", String.class));
    assertEquals("source", db.queryForObject("SELECT text_content FROM eam_knowledge_chunk WHERE tenant_id=10 AND id=4", String.class));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_chunk WHERE tenant_id=11", Integer.class));
    tx.executeWithoutResult(status -> assertFalse(repo.lockParsingVersion(10, 2)));
  }
}
