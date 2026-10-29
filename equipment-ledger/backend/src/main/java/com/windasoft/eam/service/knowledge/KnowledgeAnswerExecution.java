package com.windasoft.eam.service.knowledge;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import com.windasoft.eam.service.system.*;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Runs one authorized conversation turn; response is emitted only after citation validation. */
@Service
public class KnowledgeAnswerExecution implements org.springframework.beans.factory.DisposableBean {
  private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
  private final KnowledgeConversationService conversations;private final AuthContext auth;private final AuthorizationRepository users;
  private final Semaphore slots=new Semaphore(8);
  public KnowledgeAnswerExecution(KnowledgeConversationService conversations,AuthContext auth,AuthorizationRepository users){this.conversations=conversations;this.auth=auth;this.users=users;}
  static java.util.Map<String,Object> failure(Throwable cause){
    while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
    boolean terminal=cause instanceof KnowledgeConversationService.TurnFailed;
    if(terminal)cause=cause.getCause();
    String message=cause instanceof org.springframework.web.server.ResponseStatusException status?Objects.toString(status.getReason(),"本次回答未完成，请重试"):"本次回答未完成，请重试；如仍失败，请联系管理员检查模型配置。";
    int status=cause instanceof org.springframework.web.server.ResponseStatusException failure?failure.getStatusCode().value():502;
    return java.util.Map.of("message",message,"status",status,"code",terminal?"AI_TURN_FAILED":"REQUEST_ERROR");
  }
  public SseEmitter start(long id,String key,String question){
    return start(id,key,question,null);
  }
  public SseEmitter start(long id,String key,String question,com.fasterxml.jackson.databind.JsonNode context){
    long tenant=auth.tenantId(),user=auth.userId();
    // Check conversation ownership synchronously, before opening the stream.
    conversations.authorize(id);
    if(!slots.tryAcquire())throw new IllegalStateException("小卓当前请求较多，请稍后重试");
    var emitter=new SseEmitter(150_000L);var task=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
    Runnable cancel=()->{var running=task.get();if(running!=null)running.cancel(true);};
    emitter.onTimeout(cancel);emitter.onError(error->cancel.run());emitter.onCompletion(cancel);
    FutureTask<Void> running=new FutureTask<>(()->{
      try{
        var identity=users.activeUser(tenant,user);
        SystemExecution.run(identity,()->{try{
          emitter.send(SseEmitter.event().name("status").data("PROCESSING"));
          var result=conversations.ask(id,key,question,context);
          emitter.send(SseEmitter.event().name("answer").data(result));emitter.complete();return null;
        }catch(Exception error){throw new CompletionException(error);}});
      }catch(Exception error){
        try{emitter.send(SseEmitter.event().name("failure").data(failure(error)));emitter.complete();}catch(IOException ignored){emitter.completeWithError(error);}
      }return null;
    }){@Override public void run(){try{super.run();}finally{slots.release();}}};
    task.set(running);executor.execute(running);return emitter;
  }
  @Override public void destroy(){executor.shutdownNow();}
}
