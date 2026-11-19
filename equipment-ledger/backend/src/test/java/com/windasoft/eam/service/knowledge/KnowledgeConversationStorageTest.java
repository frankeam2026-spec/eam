package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.repository.knowledge.KnowledgeConversationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class KnowledgeConversationStorageTest {
  @Test
  void conversationMigrationAndUserTenantBoundaries() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V108__knowledge_documents.sql"),
            new ClassPathResource("db/migration/V110__knowledge_conversations.sql"))
        .execute(ds);
    var db = new JdbcTemplate(ds);
    var repo = new KnowledgeConversationRepository(db);
    repo.create(10, 20, 30);
    repo.create(11, 20, 31);
    repo.create(10, 21, 32);
    db.update(
        "INSERT INTO eam_knowledge_document(id,tenant_id,company_id,title,document_type,created_by) VALUES(1,10,1,'示例','MANUAL',20)");
    db.update(
        "INSERT INTO eam_knowledge_version(id,tenant_id,document_id,version_no,file_key,sha256,parser_version) VALUES(2,10,1,1,'file','hash','1')");
    db.update(
        "INSERT INTO eam_knowledge_page(id,tenant_id,version_id,page_no,text_content,needs_ocr) VALUES(3,10,2,1,'内容',FALSE)");
    db.update(
        "INSERT INTO eam_knowledge_chunk(id,tenant_id,page_id,sequence_no,text_content,content_hash) VALUES(101,10,3,1,'第一段','hash'),(102,10,3,2,'第二段','hash')");
    db.update(
        "INSERT INTO eam_ai_exchange(id,tenant_id,conversation_id,request_key,question,status,lease_until) VALUES(40,10,30,'request','问题','SUCCEEDED',CURRENT_TIMESTAMP)");
    db.update(
        "INSERT INTO eam_ai_exchange_citation(tenant_id,exchange_id,chunk_id,sort_no) VALUES(10,40,101,1),(10,40,102,0)");
    assertEquals(Map.of(40L, List.of(102L, 101L)), repo.citations(10, List.of(40L, 41L)));
    assertTrue(repo.citations(11, List.of(40L)).isEmpty());
    assertEquals(1, repo.count(10, 20));
    assertEquals(1, repo.list(10, 20, 1, 20).size());
    assertFalse(repo.owner(11, 20, 30, false));
    assertFalse(repo.owner(10, 21, 30, false));
    repo.delete(10, 21, 30);
    assertTrue(repo.owner(10, 20, 30, false));
    repo.delete(10, 20, 30);
    assertFalse(repo.owner(10, 20, 30, false));
    assertEquals(0, repo.count(10, 20));
  }
}
