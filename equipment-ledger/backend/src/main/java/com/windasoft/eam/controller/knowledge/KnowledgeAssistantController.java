package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.service.knowledge.KnowledgeAnswerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/assistant")
public class KnowledgeAssistantController {
  @org.springframework.beans.factory.annotation.Autowired private com.windasoft.eam.service.knowledge.KnowledgeAnswerExecution execution;
  private final KnowledgeAnswerService answers;
  private final com.windasoft.eam.service.knowledge.KnowledgeConversationService conversations;
  public KnowledgeAssistantController(KnowledgeAnswerService answers,com.windasoft.eam.service.knowledge.KnowledgeConversationService conversations){this.answers=answers;this.conversations=conversations;}
  public record Question(String question){}
  @PostMapping public Object ask(@RequestBody Question body) throws Exception {return answers.ask(body.question());}
  @PostMapping("/conversations") public Object create(){return conversations.create();}
  @GetMapping("/conversations") public Object list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
    if(page<1 || !java.util.Set.of(20,50,100).contains(size))throw new IllegalArgumentException("分页参数无效");return conversations.list(page,size);
  }
  @GetMapping("/conversations/{id}") public Object history(@PathVariable long id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size){if(page<1||!java.util.Set.of(20,50,100).contains(size))throw new IllegalArgumentException("分页参数无效");return conversations.history(id,page,size);}
  @DeleteMapping("/conversations/{id}") public Object delete(@PathVariable long id){conversations.delete(id);return java.util.Map.of("success",true);}
  public record Turn(String question,String requestKey,com.fasterxml.jackson.databind.JsonNode queryContext){}
  public record Equipment(long equipmentId){}
  @PutMapping("/conversations/{id}/equipment") public Object equipment(@PathVariable long id,@RequestBody Equipment body){conversations.bind(id,body.equipmentId());return java.util.Map.of("success",true);}
  @PostMapping("/conversations/{id}") public Object turn(@PathVariable long id,@RequestBody Turn body) throws Exception {return conversations.ask(id,body.requestKey(),body.question(),body.queryContext());}
  @PostMapping(value="/conversations/{id}/stream",produces="text/event-stream") public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@PathVariable long id,@RequestBody Turn body){return execution.start(id,body.requestKey(),body.question(),body.queryContext());}
}
