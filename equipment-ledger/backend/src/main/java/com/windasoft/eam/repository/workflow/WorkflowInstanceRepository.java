package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.repository.system.NotificationRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowInstanceRepository {
  private final JdbcTemplate db;
  private final NotificationRepository notifications;

  public WorkflowInstanceRepository(JdbcTemplate db, NotificationRepository notifications) {
    this.db = db;
    this.notifications = notifications;
  }

  public Map<String, Object> binding(long tenant, long company, String business) {
    var rows =
        db.queryForList(
            "SELECT b.*,v.template_id,v.engine_definition_id,v.execution_version FROM wf_binding b"
                + " LEFT JOIN wf_version v ON v.tenant_id=b.tenant_id AND v.id=b.version_id WHERE"
                + " b.tenant_id=? AND b.company_id=? AND b.business_type=?",
            tenant,
            company,
            business);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Map<String, Object> instance(long tenant, long id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM wf_instance WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
            tenant,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("审批实例不存在");
    return rows.getFirst();
  }

  public Map<String, Object> latest(long tenant, String business, long id) {
    var rows =
        db.queryForList(
            "SELECT * FROM wf_instance WHERE tenant_id=? AND business_type=? AND business_id=?"
                + " ORDER BY round_no DESC LIMIT 1",
            tenant,
            business,
            id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public long start(
      long tenant, long version, String business, long businessId, long company, long applicant) {
    long id = IdWorker.getId();
    int round =
        db.queryForObject(
            "SELECT COALESCE(MAX(round_no),0)+1 FROM wf_instance WHERE tenant_id=? AND"
                + " business_type=? AND business_id=?",
            Integer.class,
            tenant,
            business,
            businessId);
    db.update(
        "INSERT INTO"
            + " wf_instance(id,tenant_id,version_id,business_type,business_id,company_id,applicant_id,round_no,state)"
            + " VALUES(?,?,?,?,?,?,?,?,'RUNNING')",
        id,
        tenant,
        version,
        business,
        businessId,
        company,
        applicant,
        round);
    return id;
  }

  public void engine(long tenant, long id, String process) {
    db.update(
        "UPDATE wf_instance SET engine_instance_id=? WHERE tenant_id=? AND id=?",
        process,
        tenant,
        id);
  }

  public void finish(long tenant, long id, String state) {
    db.update(
        "UPDATE wf_instance SET state=?,row_version=row_version+1,ended_at=NOW(3) WHERE tenant_id=?"
            + " AND id=? AND state='RUNNING'",
        state,
        tenant,
        id);
    db.update(
        "UPDATE wf_approval SET state='CANCELLED' WHERE tenant_id=? AND instance_id=? AND"
            + " state='PENDING'",
        tenant,
        id);
    closeTodos(tenant, id);
  }

  public void event(long tenant, long instance, String node, String type, long actor, String text) {
    db.update(
        "INSERT INTO wf_event(id,tenant_id,instance_id,node_key,event_type,actor_id,description)"
            + " VALUES(?,?,?,?,?,?,?)",
        IdWorker.getId(),
        tenant,
        instance,
        node,
        type,
        actor,
        text);
  }

  public void snapshot(
      long tenant,
      long instance,
      Map<String, WorkflowExpression.Variable> catalog,
      Map<String, Object> values) {
    for (var variable : catalog.values()) {
      if (!variable.available()) continue;
      if (variable.key().startsWith("lines.")) {
        var rows = (List<?>) values.get("lines");
        for (int i = 0; i < rows.size(); i++)
          saveValue(
              tenant,
              instance,
              variable,
              i + 1,
              ((Map<?, ?>) rows.get(i)).get(variable.key().substring(6)));
      } else saveValue(tenant, instance, variable, 0, values.get(variable.key()));
    }
  }

  private void saveValue(
      long tenant, long instance, WorkflowExpression.Variable variable, int line, Object value) {
    String type = variable.type().name();
    db.update(
        "INSERT INTO"
            + " wf_value(id,tenant_id,instance_id,field_key,line_no,value_type,is_null,number_value,text_value,reference_value,boolean_value)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        IdWorker.getId(),
        tenant,
        instance,
        variable.key(),
        line,
        type,
        value == null,
        type.equals("NUMBER") ? value : null,
        type.equals("TEXT") ? value : null,
        type.equals("REFERENCE") ? value : null,
        type.equals("BOOLEAN") ? value : null);
  }

  public void approval(
      long tenant,
      long instance,
      String node,
      String task,
      long user,
      long businessId,
      String title) {
    long id = IdWorker.getId(), inbox = IdWorker.getId();
    db.update(
        "INSERT INTO wf_approval(id,tenant_id,instance_id,node_key,engine_task_id,user_id)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        tenant,
        instance,
        node,
        task,
        user);
    db.update(
        "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
            + " VALUES(?,?,?,?,'requisitions','领料审批',?,1)",
        inbox,
        tenant,
        user,
        businessId,
        title);
    db.update("INSERT INTO wf_todo VALUES(?,?,?)", tenant, id, inbox);
    notifications.captureSource("inv_inbox", tenant, inbox);
  }

  public List<Map<String, Object>> approvals(long tenant, long instance) {
    return db.queryForList(
        "SELECT a.*,u.display_name AS user_name,n.name AS node_name FROM wf_approval a JOIN"
            + " wf_instance i ON i.tenant_id=a.tenant_id AND i.id=a.instance_id JOIN wf_node n ON"
            + " n.tenant_id=a.tenant_id AND n.version_id=i.version_id AND n.node_key=a.node_key"
            + " JOIN eam_sys_user u ON u.tenant_id=a.tenant_id AND u.id=a.user_id WHERE"
            + " a.tenant_id=? AND a.instance_id=? ORDER BY a.id",
        tenant,
        instance);
  }

  public void act(long tenant, long id, String state, String opinion) {
    if (db.update(
            "UPDATE wf_approval SET state=?,opinion=?,acted_at=NOW(3) WHERE tenant_id=? AND id=?"
                + " AND state='PENDING'",
            state,
            opinion,
            tenant,
            id)
        != 1) throw new IllegalArgumentException("审批任务已被处理");
  }

  public void touch(long tenant, long instance) {
    db.update(
        "UPDATE wf_instance SET row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenant,
        instance);
  }

  public void cancelNode(long tenant, long instance, String task) {
    db.update(
        "UPDATE wf_approval SET state='CANCELLED' WHERE tenant_id=? AND instance_id=? AND"
            + " engine_task_id=? AND state='PENDING'",
        tenant,
        instance,
        task);
    closeTodos(tenant, instance);
  }

  public void closeTodos(long tenant, long instance) {
    db.update(
        "UPDATE inv_inbox i JOIN wf_todo t ON t.tenant_id=i.tenant_id AND t.inbox_id=i.id JOIN"
            + " wf_approval a ON a.tenant_id=t.tenant_id AND a.id=t.approval_id SET i.done=1 WHERE"
            + " a.tenant_id=? AND a.instance_id=? AND a.state<>'PENDING'",
        tenant,
        instance);
  }

  public boolean participant(long tenant, String business, long id, long user) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM wf_instance i JOIN wf_approval a ON a.tenant_id=i.tenant_id AND"
                + " a.instance_id=i.id WHERE i.tenant_id=? AND i.business_type=? AND"
                + " i.business_id=? AND a.user_id=?",
            Integer.class,
            tenant,
            business,
            id,
            user)
        > 0;
  }

  public List<Map<String, Object>> history(long tenant, String business, long id) {
    return db.queryForList(
        "SELECT i.id,i.round_no AS roundNo,i.state,i.version_id AS versionId,i.row_version AS"
            + " rowVersion,i.started_at AS startedAt,i.ended_at AS endedAt,t.name AS templateName"
            + " FROM wf_instance i JOIN wf_version v ON v.tenant_id=i.tenant_id AND"
            + " v.id=i.version_id JOIN wf_template t ON t.tenant_id=v.tenant_id AND"
            + " t.id=v.template_id WHERE i.tenant_id=? AND i.business_type=? AND i.business_id=?"
            + " ORDER BY round_no DESC",
        tenant,
        business,
        id);
  }

  public List<Map<String, Object>> events(long tenant, long instance) {
    return db.queryForList(
        "SELECT event_type AS type,node_key AS nodeKey,description,created_at AS createdAt FROM"
            + " wf_event WHERE tenant_id=? AND instance_id=? ORDER BY created_at,id",
        tenant,
        instance);
  }

  public boolean registered(long tenant, long instance, String task) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE tenant_id=? AND instance_id=? AND"
                + " engine_task_id=?",
            Integer.class,
            tenant,
            instance,
            task)
        > 0;
  }

  public Map<String, Object> values(long tenant, long instance) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> lines = new ArrayList<>();
    for (var row :
        db.queryForList(
            "SELECT * FROM wf_value WHERE tenant_id=? AND instance_id=? ORDER BY line_no,field_key",
            tenant,
            instance)) {
      Object value =
          ((Number) row.get("is_null")).intValue() == 1
              ? null
              : switch (row.get("value_type").toString()) {
                case "NUMBER" -> row.get("number_value");
                case "REFERENCE" -> row.get("reference_value");
                case "BOOLEAN" -> ((Number) row.get("boolean_value")).intValue() == 1;
                default -> row.get("text_value");
              };
      String key = row.get("field_key").toString();
      int line = ((Number) row.get("line_no")).intValue();
      if (line == 0) result.put(key, value);
      else {
        while (lines.size() < line) lines.add(new LinkedHashMap<>());
        lines.get(line - 1).put(key.substring(6), value);
      }
    }
    result.put("lines", lines);
    return result;
  }

  public void scriptResult(
      long tenant,
      long instance,
      String node,
      String execution,
      String hash,
      java.time.LocalDateTime started,
      String key,
      String type,
      Object value) {
    Object number = type.equals("NUMBER") ? value : null,
        text = type.equals("TEXT") ? value : null,
        bool = type.equals("BOOLEAN") ? value : null;
    db.update(
        "INSERT INTO"
            + " wf_value(id,tenant_id,instance_id,field_key,line_no,value_type,is_null,number_value,text_value,boolean_value)"
            + " VALUES(?,?,?,?,0,?,0,?,?,?) ON DUPLICATE KEY UPDATE"
            + " is_null=0,value_type=VALUES(value_type),number_value=VALUES(number_value),text_value=VALUES(text_value),boolean_value=VALUES(boolean_value)",
        IdWorker.getId(),
        tenant,
        instance,
        key,
        type,
        number,
        text,
        bool);
    db.update(
        "INSERT INTO"
            + " wf_script_run(id,tenant_id,instance_id,node_key,execution_id,source_hash,started_at,finished_at,value_type,number_value,text_value,boolean_value)"
            + " VALUES(?,?,?,?,?,?,?,NOW(3),?,?,?,?)",
        IdWorker.getId(),
        tenant,
        instance,
        node,
        execution,
        hash,
        started,
        type,
        number,
        text,
        bool);
  }
}
