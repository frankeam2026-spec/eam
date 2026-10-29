package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.windasoft.eam.repository.knowledge.KnowledgeConversationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class KnowledgeConversationStorageTest {
  @Test void conversationMigrationAndUserTenantBoundaries(){
    var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V108__knowledge_documents.sql"),new ClassPathResource("db/migration/V110__knowledge_conversations.sql")).execute(ds);
    var repo=new KnowledgeConversationRepository(new JdbcTemplate(ds));
    repo.create(10,20,30);repo.create(11,20,31);repo.create(10,21,32);
    assertEquals(1,repo.count(10,20));assertEquals(1,repo.list(10,20,1,20).size());
    assertFalse(repo.owner(11,20,30,false));assertFalse(repo.owner(10,21,30,false));
    repo.delete(10,21,30);assertTrue(repo.owner(10,20,30,false));
    repo.delete(10,20,30);assertFalse(repo.owner(10,20,30,false));assertEquals(0,repo.count(10,20));
  }
}
