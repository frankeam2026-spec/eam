package com.windasoft.eam.service.system;

import static com.windasoft.eam.repository.system.NotificationRepository.yes;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts.*;
import com.windasoft.eam.repository.system.NotificationRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationPolicyService {
  public static final Set<String> KINDS=Set.of("repair","daily","periodic","maintenance");
  public static final Set<String> REPAIR_SCENES=Set.of("WAIT_DISPATCH","REPORT_UNCLAIMED","WAIT_CLAIM","PROCESS","WAIT_REVIEW");
  public static final Set<String> TASK_SCENES=Set.of("BEFORE_DUE","AFTER_DUE","WAIT_REVIEW");
  private final NotificationRepository repo;
  private final AuthContext auth;
  private final PermissionService permissions;
  public NotificationPolicyService(NotificationRepository repo,AuthContext auth,PermissionService permissions) {this.repo=repo;this.auth=auth;this.permissions=permissions;}
  static void require(boolean valid,String message) {if(!valid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  public Map<String,Object> list(String kind,String q,int page,int size) {
    permissions.require("notificationPolicy","查看");
    return Map.of("data",repo.policyRows(auth.tenantId(),kind,q,page,size),"total",repo.policyCount(auth.tenantId(),kind,q));
  }
  public Map<String,Object> choices(String kind,String q,int page,int size) {
    require(KINDS.contains(kind),"提醒业务类型无效");
    if(!permissions.allowed("notificationPolicy","查看")) {
      if(kind.equals("repair"))permissions.require("repair","查看");
      else if(kind.equals("maintenance"))permissions.require("maintenancePlan","查看");
      else permissions.require(kind.equals("daily")?"inspectionDailyPlan":"inspectionPeriodicPlan","查看");
    }
    return repo.policyChoices(auth.tenantId(),kind,q,page,size);
  }
  public Map<String,Object> detail(long id) {
    permissions.require("notificationPolicy","查看");
    var p=repo.policy(auth.tenantId(),id,false);
    if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提醒策略不存在");
    return view(p);
  }
  private Map<String,Object> view(Map<String,Object> p) {
    long tenant=auth.tenantId(),id=((Number)p.get("id")).longValue();
    var people=repo.people(tenant,id); var rules=repo.rules(tenant,id);
    for(var r:rules) {
      String key=r.get("id").toString(); r.put("enabled",yes(r.get("enabled")));
      for(var role:List.of("TO","CC")) r.put(role.equals("TO")?"recipients":"cc",people.stream().filter(x->key.equals(x.get("ruleId")) && role.equals(x.get("recipientKind"))).map(x->x.get("userId")).toList());
    }
    return Map.of("id",Long.toString(id),"name",p.get("name"),"businessKind",p.get("business_kind"),"enabled",yes(p.get("enabled")),"rowVersion",p.get("row_version"),"rules",rules,"people",people);
  }
  @Transactional
  public Map<String,Object> save(Policy input) {
    permissions.require("notificationPolicy","编辑");
    require(KINDS.contains(input.businessKind()),"提醒业务类型无效");
    long tenant=auth.tenantId(),id=input.id()==null?IdWorker.getId():input.id();
    var old=input.id()==null?null:repo.policy(tenant,id,true);
    if(input.id()!=null && old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提醒策略不存在");
    if(old!=null) {VersionGuard.checkPayload((Number)old.get("row_version"),Map.of("rowVersion",input.rowVersion()==null?-1:input.rowVersion())); require(input.businessKind().equals(old.get("business_kind")),"已有策略不能更换业务类型");}
    require(!input.enabled() || input.rules().stream().anyMatch(Rule::enabled),"启用策略至少需要一条有效规则");
    var before=old==null?List.<Map<String,Object>>of():repo.rules(tenant,id);
    var beforePeople=old==null?List.<Map<String,Object>>of():repo.people(tenant,id);
    var ids=new HashSet<Long>();
    for(var r:input.rules()) {
      require((input.businessKind().equals("repair")?REPAIR_SCENES:TASK_SCENES).contains(r.scene()),"提醒场景无效");
      require(Set.of("MINUTE","HOUR","DAY").contains(r.unit()),"提醒时间单位无效");
      require(!r.enabled() || !r.recipients().isEmpty(),"启用规则必须选择提醒人");
      require(r.recipients().size()+r.cc().size()<=200,"每条提醒最多选择200人");
      if(r.id()!=null)require(ids.add(r.id()) && before.stream().anyMatch(x->x.get("id").toString().equals(r.id().toString())),"规则ID无效或重复");
      for(long u:new LinkedHashSet<>(java.util.stream.Stream.concat(r.recipients().stream(),r.cc().stream()).toList()))require(repo.activeUser(tenant,u),"提醒人员不存在、停用或不属于当前租户");
    }
    repo.savePolicy(tenant,id,input.name().trim(),input.businessKind(),input.enabled(),old==null);
    int sort=0;
    for(var r:input.rules()) {
      long rid=r.id()==null?IdWorker.getId():r.id();
      var prior=before.stream().filter(x->x.get("id").toString().equals(Long.toString(rid))).findFirst().orElse(null);
      var to=new TreeSet<>(r.recipients()); var cc=new TreeSet<>(r.cc());cc.removeAll(to);
      var previousTo=new TreeSet<String>();var previousCc=new TreeSet<String>();
      for(var person:beforePeople)if(person.get("ruleId").toString().equals(Long.toString(rid)))(person.get("recipientKind").equals("TO")?previousTo:previousCc).add(person.get("userId").toString());
      boolean changed=prior==null || !r.scene().equals(prior.get("scene")) || r.value()!=((Number)prior.get("value")).intValue() || !r.unit().equals(prior.get("unit")) || r.enabled()!=yes(prior.get("enabled")) || !previousTo.equals(new TreeSet<>(to.stream().map(Object::toString).toList())) || !previousCc.equals(new TreeSet<>(cc.stream().map(Object::toString).toList()));
      repo.saveRule(tenant,id,rid,r.scene(),r.value(),r.unit(),r.enabled(),sort++,changed);
      to.forEach(u->repo.person(tenant,rid,u,"TO"));cc.forEach(u->repo.person(tenant,rid,u,"CC"));
    }
    for(var r:before)if(!ids.contains(Long.valueOf(r.get("id").toString())))repo.disableRule(tenant,Long.parseLong(r.get("id").toString()));
    return detail(id);
  }
  public List<Map<String,Object>> defaults() { permissions.require("notificationPolicy","查看");return repo.defaults(auth.tenantId()); }
  @Transactional public void setDefault(String kind,DefaultPolicy input) {
    permissions.require("notificationPolicy","编辑");require(KINDS.contains(kind),"提醒业务类型无效");
    long tenant=auth.tenantId();var old=repo.lockDefault(tenant,kind);VersionGuard.checkPayload((Number)old.get("row_version"),Map.of("rowVersion",input.rowVersion()));
    validatePolicy(tenant,kind,input.policyId());repo.setDefault(tenant,kind,input.policyId());
  }
  private void validatePolicy(long tenant,String kind,Long id) {
    if(id==null)return;var p=repo.policy(tenant,id,false);
    require(p!=null && kind.equals(p.get("business_kind")) && yes(p.get("enabled")),"请选择本业务已启用的提醒策略");
  }
  public void saveBinding(long tenant,String kind,long id,Map<String,Object> body) {
    if(!body.containsKey("reminderMode"))return;
    String mode=Objects.toString(body.get("reminderMode"),"DEFAULT");require(Set.of("DEFAULT","POLICY","OFF").contains(mode),"提醒策略模式无效");
    Long selected=mode.equals("POLICY") && body.get("reminderPolicyId")!=null && !body.get("reminderPolicyId").toString().isBlank()?Long.valueOf(body.get("reminderPolicyId").toString()):null;
    require(!mode.equals("POLICY") || selected!=null,"请选择提醒策略");validatePolicy(tenant,kind,selected);repo.binding(tenant,kind,id,mode,selected);
  }
  public void attachBinding(long tenant,String kind,long id,Map<String,Object> view) {view.putAll(repo.binding(tenant,kind,id));}
  public Map<String,Object> users(String q,int page,int size) {permissions.require("notificationPolicy","查看");return Map.of("data",repo.users(auth.tenantId(),q,page,size),"total",repo.userCount(auth.tenantId(),q));}
}
