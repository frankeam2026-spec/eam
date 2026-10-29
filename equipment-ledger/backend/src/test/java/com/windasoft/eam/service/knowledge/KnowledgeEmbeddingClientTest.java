package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeEmbeddingClientTest {
  final KnowledgeEmbeddingClient client=new KnowledgeEmbeddingClient(new ObjectMapper(),"","","");
  @Test void restoresProviderOrderToAvoidAttachingWrongVectors() throws Exception {
    assertEquals(List.of(List.of(1.0,2.0),List.of(3.0,4.0)),client.decode(
        "{\"data\":[{\"index\":1,\"embedding\":[3,4]},{\"index\":0,\"embedding\":[1,2]}]}",2));
  }
  @Test void rejectsDuplicatesMissingRowsAndInconsistentDimensions(){
    assertThrows(IllegalStateException.class,()->client.decode("{\"data\":[]}",1));
    assertThrows(IllegalStateException.class,()->client.decode("{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]}",2));
    assertThrows(IllegalStateException.class,()->client.decode("{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[2,3]}]}",2));
  }
  @Test void missingConfigurationFailsWithoutMakingRemoteCalls(){
    assertThrows(IllegalStateException.class,()->client.embed(List.of("manual")));
  }
}
