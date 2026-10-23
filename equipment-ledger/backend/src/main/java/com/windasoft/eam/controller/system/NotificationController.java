package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.system.NotificationContracts.*;
import com.windasoft.eam.service.system.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationPolicyService policies;private final NotificationChannelService channels;
  private final NotificationRetentionService retention;
  public NotificationController(NotificationPolicyService policies,NotificationChannelService channels,NotificationRetentionService retention){this.policies=policies;this.channels=channels;this.retention=retention;}
  static int size(int n){return Math.max(1,Math.min(n,100));}
  @GetMapping("/policies") public Object list(@RequestParam(defaultValue="")String kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return policies.list(kind,q,Math.max(1,page),size(size));}
  @GetMapping("/policies/choices") public Object choices(@RequestParam String kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size){return policies.choices(kind,q,Math.max(1,page),size(size));}
  @GetMapping("/policies/{id}") public Object detail(@PathVariable long id){return Map.of("data",policies.detail(id));}
  @PostMapping("/policies") public Object save(@Valid @RequestBody Policy body){return Map.of("data",policies.save(body));}
  @GetMapping("/defaults") public Object defaults(){return Map.of("data",policies.defaults());}
  @PutMapping("/defaults/{kind}") public Object defaultPolicy(@PathVariable String kind,@Valid @RequestBody DefaultPolicy body){policies.setDefault(kind,body);return defaults();}
  @GetMapping("/users") public Object users(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size){return policies.users(q,Math.max(1,page),size(size));}
  @GetMapping("/channels") public Object channels(){return Map.of("data",channels.settings());}
  @PutMapping("/channels") public Object channels(@Valid @RequestBody Channel body){channels.save(body);return channels();}
  @PostMapping("/channels/test-email") public Object test(@Valid @RequestBody TestEmail body){channels.test(body.address());return Map.of("success",true);}
  @GetMapping("/deliveries") public Object deliveries(@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String channel,@RequestParam(defaultValue="")String q,@RequestParam(required=false)java.time.LocalDate from,@RequestParam(required=false)java.time.LocalDate to,@RequestParam(defaultValue="")String businessKind,@RequestParam(required=false)Long recipientId,@RequestParam(defaultValue="false")boolean showInactive,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return channels.deliveries(status,channel,q,from,to,businessKind,recipientId,showInactive,Math.max(1,page),size(size));}
  @GetMapping("/deliveries/recipients") public Object recipients(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size){return channels.deliveryRecipients(q,Math.max(1,page),size(size));}
  @GetMapping("/deliveries/retention") public Object retention(){return Map.of("data",retention.settings());}
  @PutMapping("/deliveries/retention") public Object retention(@Valid @RequestBody Retention body){return Map.of("data",retention.save(body));}
  @PostMapping("/deliveries/{id}/retry") public Object retry(@PathVariable long id,@Valid @RequestBody Retry body){channels.retry(id,body.attempts());return Map.of("success",true);}
}
