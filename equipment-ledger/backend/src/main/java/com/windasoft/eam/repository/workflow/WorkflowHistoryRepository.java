package com.windasoft.eam.repository.workflow;

import com.windasoft.eam.dto.workflow.WorkflowHistoryHeader;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound.Approval;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound.Event;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 历史只读查询：按同一业务对象批量联查轮次，不包含流程推进或业务授权。 */
@Repository
public class WorkflowHistoryRepository {
  private final NamedParameterJdbcTemplate db;

  public WorkflowHistoryRepository(NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public List<WorkflowHistoryHeader> rounds(long tenant, String business, long id) {
    return db.query(
        """
        SELECT i.id,i.round_no,i.state,i.version_id,i.row_version,i.started_at,i.ended_at,t.name
        FROM wf_instance i
        JOIN wf_version v ON v.tenant_id=i.tenant_id AND v.id=i.version_id
        JOIN wf_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id
        WHERE i.tenant_id=:tenant AND i.business_type=:business AND i.business_id=:id
        ORDER BY i.round_no DESC
        """,
        Map.of("tenant", tenant, "business", business, "id", id),
        (r, n) ->
            new WorkflowHistoryHeader(
                r.getLong("id"),
                r.getInt("round_no"),
                r.getString("state"),
                r.getLong("version_id"),
                r.getLong("row_version"),
                r.getObject("started_at", LocalDateTime.class),
                r.getObject("ended_at", LocalDateTime.class),
                r.getString("name")));
  }

  public List<Approval> approvals(long tenant, List<Long> instances) {
    if (instances.isEmpty()) {
      return List.of();
    }
    // 与原历史保持内连接及排序，停用/删除用户的名称仍来自同租户真实关联。
    return db.query(
        """
SELECT a.*,u.display_name AS user_name,n.name AS node_name
FROM wf_approval a
JOIN wf_instance i ON i.tenant_id=a.tenant_id AND i.id=a.instance_id
JOIN wf_node n ON n.tenant_id=a.tenant_id AND n.version_id=i.version_id AND n.node_key=a.node_key
JOIN eam_sys_user u ON u.tenant_id=a.tenant_id AND u.id=a.user_id
WHERE a.tenant_id=:tenant AND a.instance_id IN (:instances)
ORDER BY a.id
""",
        Map.of("tenant", tenant, "instances", instances),
        (r, n) ->
            new Approval(
                r.getLong("id"),
                r.getLong("tenant_id"),
                r.getLong("instance_id"),
                r.getString("node_key"),
                r.getString("engine_task_id"),
                r.getLong("user_id"),
                r.getString("state"),
                r.getString("opinion"),
                r.getObject("acted_at", LocalDateTime.class),
                r.getString("user_name"),
                r.getString("node_name")));
  }

  public record InstanceEvent(long instanceId, Event event) {}

  public List<InstanceEvent> events(long tenant, List<Long> instances) {
    if (instances.isEmpty()) {
      return List.of();
    }
    return db.query(
        """
        SELECT instance_id,event_type,node_key,description,created_at FROM wf_event
        WHERE tenant_id=:tenant AND instance_id IN (:instances) ORDER BY created_at,id
        """,
        Map.of("tenant", tenant, "instances", instances),
        (r, n) ->
            new InstanceEvent(
                r.getLong("instance_id"),
                new Event(
                    r.getString("event_type"),
                    r.getString("node_key"),
                    r.getString("description"),
                    r.getObject("created_at", LocalDateTime.class))));
  }
}
