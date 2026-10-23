package com.windasoft.eam.service.system;

import static com.windasoft.eam.repository.system.NotificationRepository.yes;
import com.windasoft.eam.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.system.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {
  private final ReminderRepository repo;
  private final NotificationRepository notifications;
  private final NotificationChannelService channels;
  private final ReminderService reminders;
  private final ObjectMapper json;
  public NotificationDeliveryService(ReminderRepository repo,NotificationRepository notifications,NotificationChannelService channels,ReminderService reminders,ObjectMapper json){this.repo=repo;this.notifications=notifications;this.channels=channels;this.reminders=reminders;this.json=json;}
  public void deliver(long tenant,long id) {
    var m=repo.claim(tenant,id);if(m.isEmpty())return;int attempt=((Number)m.get("attempts")).intValue();
    try {
      if(m.get("cancelled_at")!=null || !reminders.stillValid(m)) {repo.cancel(tenant,((Number)m.get("id")).longValue());repo.finish(id,attempt,"CANCELLED","任务阶段或策略已变化");return;}
      if(!yes(m.get("user_status")) || yes(m.get("user_deleted"))) {repo.finish(id,attempt,"MISSING_ADDRESS","接收用户已停用或删除");return;}
      if(m.get("channel").equals("SYSTEM")){repo.finish(id,attempt,"SENT",null);return;}
      var config=notifications.channel(tenant,false);
      if(!yes(config.get("email_enabled"))){repo.finish(id,attempt,"DISABLED","系统未启用邮件渠道");return;}
      String address=Objects.toString(m.get("email"),"");
      if(address.isBlank()){repo.finish(id,attempt,"MISSING_ADDRESS","接收用户未配置邮箱");return;}
      String subject=m.get("subject").toString();
      if(m.get("message_key")!=null)try {subject=MessageContext.render(new MessageRef(m.get("message_key").toString(),json.readValue(m.get("message_args").toString(),new TypeReference<Map<String,Object>>(){})));}catch(Exception ignored){}
      String url=Objects.toString(config.get("public_url"),"");String module=m.get("module").toString(),object=m.get("object_id").toString();
      String path=switch(module){case "repair"->"/repairs/"+object;case "inspection"->"/inspection/task/"+object;case "maintenance"->"/maintenance/task/"+object;default->"/inbox";};
      String content=subject+"\n"+Objects.toString(m.get("content"),"")+(url.isBlank()?"":"\n查看任务："+url.replaceAll("/+$","")+"/#"+path);
      channels.send(config,address,m.get("recipient_kind").equals("CC"),subject,content);repo.finish(id,attempt,"SENT",null);
    } catch(org.springframework.mail.MailSendException uncertain) {
      // SMTP may have accepted DATA before a connection timeout. Never blindly duplicate it.
      boolean connectionFailure=false;
      for(Throwable cause=uncertain;cause!=null;cause=cause.getCause())
        if(cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException || cause.getClass().getSimpleName().equals("MailConnectException"))connectionFailure=true;
      repo.finish(id,attempt,connectionFailure?"FAILED":"UNKNOWN",connectionFailure?"SMTP连接失败":"邮件发送结果待确认，请核查邮件服务器记录");
    } catch(Exception e) {repo.finish(id,attempt,"FAILED","发送失败："+e.getClass().getSimpleName());}
  }
}
