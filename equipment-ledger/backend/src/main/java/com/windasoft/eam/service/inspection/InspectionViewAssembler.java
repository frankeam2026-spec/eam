package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InspectionViewAssembler {
  private final ObjectMapper json;
  private final InspectionRepository repository;
  private final InspectionTaskAccessService access;

  public InspectionViewAssembler(
      ObjectMapper json, InspectionRepository repository, InspectionTaskAccessService access) {
    this.json = json;
    this.repository = repository;
    this.access = access;
  }

  public Map<String, Object> definition(Map<String, Object> row) {
    var v = decode(row.get("payload"));
    if (text(row, "kind").equals("daily-plans")) {
      v.put("typeId", "");
      v.put("typeName", "");
    }
    v.put("id", row.get("id").toString());
    v.put("code", row.get("code"));
    v.put("name", row.get("name"));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("rowVersion", row.get("row_version"));
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    if (text(row, "kind").equals("types")) v.putIfAbsent("sortOrder", 0);
    if (text(row, "kind").endsWith("plans") && !row.containsKey("query_nextAt")) {
      var cursor = repository.listScheduleDates(row.get("tenant_id"), row.get("id"));
      if (!cursor.isEmpty()) v.put("nextAt", dateTime(cursor.getFirst().get("next_at")).toString());
    }
    derived(row, v);
    return v;
  }

  public Map<String, Object> task(Map<String, Object> row) {
    var v = decode(row.get("snapshot"));
    v.put("typeId", Objects.toString(row.get("type_id"), ""));
    v.put("planId", Objects.toString(row.get("plan_id"), ""));
    v.put("companyId", Objects.toString(row.get("company_id"), ""));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("id", row.get("id").toString());
    v.put("code", row.get("code"));
    v.put("kind", row.get("kind"));
    v.put("arrangementName", text(row, "arrangement_name"));
    v.put("businessDate", text(row, "business_date"));
    v.put("followUpUserId", text(row, "follow_up_user_id"));
    v.put(
        "assignmentMissing",
        text(row, "kind").equals("daily")
            && !text(v, "personMode").equals("现场直接点检")
            && ids(v.get("owners")).isEmpty());
    v.put(
        "canExempt",
        text(row, "kind").equals("daily")
            && access.access(row, "免检")
            && Set.of("未开始", "待执行", "已过期").contains(text(row, "status")));
    v.put("exemptionReason", text(row, "exemption_reason"));
    v.put("exemptedAt", text(row, "exempted_at"));
    v.put(
        "exemptionAfterDeadline",
        row.get("exempted_at") != null
            && dateTime(row.get("exempted_at")).isAfter(dateTime(row.get("expires_at"))));
    v.put("status", row.get("status"));
    v.put("dueAt", dateTime(row.get("due_at")).toString());
    v.put("expiresAt", dateTime(row.get("expires_at")).toString());
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    v.put("rowVersion", row.get("row_version"));
    v.put("ownerId", Objects.toString(row.get("owner_id"), ""));
    v.put(
        "ownerName",
        row.containsKey("query_ownerName")
            ? row.get("query_ownerName")
            : userName(number(row.get("tenant_id")), nullable(row.get("owner_id"))));
    var result =
        row.get("result") == null ? new LinkedHashMap<String, Object>() : decode(row.get("result"));
    v.put("result", result);
    if (!text(result, "startedAt").isBlank()) v.put("canExempt", false);
    boolean expiredStart =
        text(row, "kind").equals("daily")
            && row.get("arrangement_id") != null
            && Set.of("未开始", "待执行", "已过期", "审核不通过").contains(text(row, "status"))
            && now().isAfter(dateTime(row.get("expires_at")))
            && !yes(v.get("allowLate"));
    v.put("canExecute", access.execute(row) && !expiredStart);
    v.put("canReview", access.review(row));
    v.put("canAmendResult", access.canAmendResult(row));
    v.put("canTransfer", access.access(row, "转派"));
    v.put("canVoid", access.access(row, "删除"));
    v.put("canDelete", access.canDelete(row));
    var items = rows(v.get("items"));
    var answers = rows(result.get("items"));
    v.put("expected", items.size());
    v.put(
        "checked",
        answers.stream()
            .filter(
                a ->
                    !yes(a.get("skipped"))
                        && (!text(a, "value").isBlank() || yes(a.get("implicitNormal"))))
            .count());
    v.put(
        "abnormal",
        answers.stream().filter(a -> !yes(a.get("skipped")) && yes(a.get("abnormal"))).count());
    v.put("skipped", strings(result.get("skippedEquipmentIds")).size());
    v.put("equipmentCount", rows(v.get("equipment")).size());
    v.put(
        "missing",
        items.stream()
            .filter(
                i -> !strings(result.get("skippedEquipmentIds")).contains(text(i, "equipmentId")))
            .filter(
                i ->
                    answers.stream()
                        .noneMatch(
                            a ->
                                text(a, "id").equals(text(i, "id"))
                                    && (!text(a, "value").isBlank()
                                        || yes(a.get("implicitNormal")))))
            .count());
    var deadline = dateTime(row.get("expires_at"));
    String finishedAt = text(result, "finishedAt");
    LocalDateTime completion = finishedAt.isBlank() ? now() : LocalDateTime.parse(finishedAt);
    v.put("overdue", completion.isAfter(deadline));
    v.put(
        "delayDays",
        Math.max(0, (Duration.between(deadline, completion).getSeconds() + 86399) / 86400));
    String start = text(result, "startedAt"), end = text(result, "finishedAt");
    v.put(
        "workSeconds",
        start.isBlank()
            ? 0
            : Math.max(
                0,
                Duration.between(
                        LocalDateTime.parse(start),
                        end.isBlank() ? now() : LocalDateTime.parse(end))
                    .getSeconds()));
    derived(row, v);
    return v;
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }

  static void derived(Map<String, Object> row, Map<String, Object> view) {
    row.forEach(
        (key, value) -> {
          if (key.startsWith("query_") && !key.equals("query_enabled"))
            view.put(key.substring(6), value);
        });
  }

  String userName(long tenant, Long id) {
    if (id == null) return "";
    var rows = repository.findUserName(tenant, id);
    return rows.isEmpty() ? "" : Objects.toString(rows.getFirst().get("display_name"), "");
  }
}
