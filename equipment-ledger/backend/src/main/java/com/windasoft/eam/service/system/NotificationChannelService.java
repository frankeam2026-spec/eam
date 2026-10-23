package com.windasoft.eam.service.system;

import static com.windasoft.eam.repository.system.NotificationRepository.yes;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts.*;
import com.windasoft.eam.repository.system.*;
import java.util.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationChannelService {
  private final NotificationRepository repo;
  private final NotificationSecret secrets;
  private final PermissionService permissions;
  private final AuthContext auth;
  public NotificationChannelService(NotificationRepository repo,NotificationSecret secrets,PermissionService permissions,AuthContext auth){this.repo=repo;this.secrets=secrets;this.permissions=permissions;this.auth=auth;}
  public Map<String,Object> settings() {
    permissions.require("notificationChannel","查看");var c=repo.channel(auth.tenantId(),false);
    var result=new LinkedHashMap<String,Object>();
    result.put("rowVersion",c.get("row_version"));result.put("systemEnabled",yes(c.get("system_enabled")));result.put("emailEnabled",yes(c.get("email_enabled")));
    result.put("smtpHost",c.get("smtp_host"));result.put("smtpPort",c.get("smtp_port"));result.put("smtpSecurity",c.get("smtp_security"));result.put("smtpUsername",c.get("smtp_username"));result.put("senderAddress",c.get("sender_address"));result.put("publicUrl",c.get("public_url"));
    result.put("hasPassword",c.get("smtp_secret")!=null && !c.get("smtp_secret").toString().isBlank());result.put("appStatus","NOT_INTEGRATED");return result;
  }
  @Transactional public void save(Channel c) {
    permissions.require("notificationChannel","编辑");
    NotificationPolicyService.require(c.systemEnabled(),"站内消息为公共通知基础渠道，须保持启用");
    NotificationPolicyService.require(Set.of("NONE","STARTTLS","SSL").contains(c.smtpSecurity()),"SMTP安全方式无效");
    NotificationPolicyService.require(!c.emailEnabled() || (c.smtpHost()!=null && !c.smtpHost().isBlank() && c.senderAddress()!=null && !c.senderAddress().isBlank()),"启用邮件必须配置SMTP主机和发件邮箱");
    NotificationPolicyService.require(c.publicUrl()==null || c.publicUrl().isBlank() || c.publicUrl().matches("https?://[^\\s]+"),"系统访问地址必须是HTTP或HTTPS地址");
    var old=repo.channel(auth.tenantId(),true);VersionGuard.checkPayload((Number)old.get("row_version"),Map.of("rowVersion",c.rowVersion()));
    String secret=Objects.toString(old.get("smtp_secret"),"");if(c.password()!=null && !c.password().isEmpty())secret=secrets.encrypt(c.password());repo.saveChannel(auth.tenantId(),c,secret);
  }
  public void test(String address) {
    permissions.require("notificationChannel","编辑");var c=repo.channel(auth.tenantId(),false);
    send(c,address,false,"设备管理系统邮件测试","邮件渠道测试成功。");
  }
  public Map<String,Object> deliveries(String status,String channel,String q,int page,int size) {permissions.require("notificationDelivery","查看");return repo.deliveryPage(auth.tenantId(),status,channel,q,page,size);}
  public Map<String,Object> deliveries(String status,String channel,String q,java.time.LocalDate from,java.time.LocalDate to,String kind,Long recipient,boolean showInactive,int page,int size) {
    permissions.require("notificationDelivery","查看");
    var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    if(from==null)from=today.minusDays(6);if(to==null)to=today;
    NotificationPolicyService.require(!to.isBefore(from) && to.getYear()<9999,"发送记录起止日期无效");
    NotificationPolicyService.require(kind.isEmpty() || NotificationPolicyService.KINDS.contains(kind),"提醒业务类型无效");
    NotificationPolicyService.require(channel.isEmpty() || Set.of("SYSTEM","EMAIL","APP").contains(channel),"通知渠道无效");
    NotificationPolicyService.require(status.isEmpty() || Set.of("PENDING","SENDING","SENT","FAILED","UNKNOWN","MISSING_ADDRESS","DISABLED","NOT_INTEGRATED","CANCELLED").contains(status),"发送状态无效");
    return repo.deliveryPage(auth.tenantId(),new DeliveryFilter(status,channel,q,from.atStartOfDay(),to.plusDays(1).atStartOfDay(),kind,recipient,showInactive),page,size);
  }
  public Map<String,Object> deliveryRecipients(String q,int page,int size) {
    permissions.require("notificationDelivery","查看");return repo.deliveryRecipients(auth.tenantId(),q,page,size);
  }
  @Transactional public void retry(long id,int attempts) {permissions.require("notificationDelivery","重试");VersionGuard.updated(repo.retry(auth.tenantId(),id,attempts));}
  public void send(Map<String,Object> config,String address,boolean cc,String subject,String content) {
    var sender=new JavaMailSenderImpl();sender.setHost(Objects.toString(config.get("smtp_host"),""));sender.setPort(((Number)config.get("smtp_port")).intValue());
    sender.setUsername(Objects.toString(config.get("smtp_username"),""));sender.setPassword(secrets.decrypt(Objects.toString(config.get("smtp_secret"),"")));sender.setDefaultEncoding("UTF-8");
    var props=sender.getJavaMailProperties();props.put("mail.smtp.auth",!sender.getUsername().isBlank());props.put("mail.smtp.connectiontimeout",5000);props.put("mail.smtp.timeout",5000);props.put("mail.smtp.writetimeout",5000);
    String security=Objects.toString(config.get("smtp_security"),"STARTTLS");props.put("mail.smtp.starttls.enable",security.equals("STARTTLS"));props.put("mail.smtp.starttls.required",security.equals("STARTTLS"));props.put("mail.smtp.ssl.enable",security.equals("SSL"));props.put("mail.smtp.ssl.checkserveridentity",true);
    var mail=new SimpleMailMessage();mail.setFrom(Objects.toString(config.get("sender_address")));if(cc)mail.setCc(address);else mail.setTo(address);mail.setSubject(subject);mail.setText(content);sender.send(mail);
  }
}
