package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.*;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyArrangementService {
  private final DailyArrangementRepository repository;
  private final PermissionService permissions;
  private final AuthContext auth;
  private final InspectionValidationService validation;

  public DailyArrangementService(
      DailyArrangementRepository repository,
      PermissionService permissions,
      AuthContext auth,
      InspectionValidationService validation) {
    this.repository = repository;
    this.permissions = permissions;
    this.auth = auth;
    this.validation = validation;
  }

  private void check(boolean ok, String field, String key) {
    if (!ok) throw new FieldValidationException(field, MessageRef.of("daily.error." + key));
  }

  public void validate(long tenant, Long planId, Map<String, Object> plan) {
    var groups = rows(plan.get("arrangements"));
    check(!groups.isEmpty() && groups.size() <= 30, "arrangements", "arrangements");
    var equipment =
        new HashSet<>(
            ids(rows(plan.get("details")).stream().map(d -> d.get("equipmentId")).toList()));
    var activeIds =
        repository.activePeople(tenant).stream()
            .map(u -> number(u.get("id")))
            .collect(java.util.stream.Collectors.toSet());
    var keys = new HashSet<Long>();
    for (int i = 0; i < groups.size(); i++) {
      var a = groups.get(i);
      String path = "arrangements." + i + ".";
      if (planId == null || text(a, "id").isBlank()) a.put("id", Long.toString(IdWorker.getId()));
      long id = number(a.get("id"));
      check(id > 0 && keys.add(id), path + "name", "arrangements");
      Long owner = repository.ownerPlan(tenant, id);
      check(owner == null || Objects.equals(owner, planId), path + "name", "arrangements");
      check(!text(a, "name").isBlank() && text(a, "name").length() <= 150, path + "name", "name");
      a.put("effectiveFrom", now().toString());
      String f = text(a, "frequency");
      check(
          Set.of("daily", "weekly", "monthly", "shift", "slots", "interval", "event").contains(f),
          path + "frequency",
          "frequency");
      check(
          number(a.get("intervalCount")) >= 1 && number(a.get("intervalCount")) <= 10000,
          path + "intervalCount",
          "interval");
      check(
          number(a.get("windowMinutes")) >= 1 && number(a.get("windowMinutes")) <= 10080,
          path + "windowMinutes",
          "window");
      LocalDate anchor;
      try {
        anchor = LocalDate.parse(text(a, "anchorDate"));
      } catch (Exception ex) {
        throw new FieldValidationException(path + "anchorDate", MessageRef.of("daily.error.date"));
      }
      check(
          !anchor.isBefore(LocalDate.parse(text(plan, "startDate")))
              && !anchor.isAfter(LocalDate.parse(text(plan, "endDate"))),
          path + "anchorDate",
          "date");
      try {
        LocalTime.parse(text(a, "startTime"));
        LocalTime.parse(text(a, "endTime"));
      } catch (Exception ex) {
        throw new FieldValidationException(path + "startTime", MessageRef.of("daily.error.time"));
      }
      if (Set.of("weekly", "monthly").contains(f))
        check(
            !ids(a.get("days")).isEmpty()
                && ids(a.get("days")).stream()
                    .allMatch(d -> d >= 1 && d <= (f.equals("weekly") ? 7 : 31)),
            path + "days",
            "days");
      if (Set.of("shift", "slots").contains(f)) {
        var slots = rows(a.get("slots"));
        check(!slots.isEmpty() && slots.size() <= 24, path + "slots", "slots");
        var seen = new HashSet<String>();
        for (var slot : slots) {
          if (text(slot, "id").isBlank()) slot.put("id", Long.toString(IdWorker.getId()));
          check(
              text(slot, "id").length() <= 32
                  && seen.add(text(slot, "id"))
                  && !text(slot, "name").isBlank()
                  && text(slot, "name").length() <= 100,
              path + "slots",
              "slots");
          try {
            LocalTime.parse(text(slot, "start"));
            LocalTime.parse(text(slot, "end"));
          } catch (Exception ex) {
            throw new FieldValidationException(path + "slots", MessageRef.of("daily.error.time"));
          }
        }
      }
      String mode = text(a, "personMode");
      check(Set.of("specified", "team", "roster").contains(mode), path + "personMode", "people");
      check(activeIds.contains(number(a.get("followUpUserId"))), path + "followUpUserId", "people");
      if (mode.equals("specified"))
        check(!ids(a.get("owners")).isEmpty(), path + "owners", "people");
      check(activeIds.containsAll(ids(a.get("owners"))), path + "owners", "people");
      if (mode.equals("team"))
        check(
            teams().stream().anyMatch(t -> text(t, "id").equals(text(a, "teamId")))
                && !repository.teamMembers(tenant, number(a.get("teamId"))).isEmpty(),
            path + "teamId",
            "people");
      if (mode.equals("roster")) check(f.equals("shift"), path + "personMode", "roster");
      check(equipment.containsAll(ids(a.get("equipmentIds"))), path + "equipmentIds", "equipment");
      if (f.equals("event"))
        check(
            Set.of("startup", "shutdown", "changeover", "temporary").contains(text(a, "eventType")),
            path + "eventType",
            "frequency");
      var details = details(plan, a);
      check(!details.isEmpty(), path + "equipmentIds", "equipment");
      check(
          details.stream().allMatch(d -> !rows(d.get("projectRefs")).isEmpty()),
          path + "projectRefs",
          "projects");
      var allRefs =
          rows(plan.get("details")).stream()
              .flatMap(d -> rows(d.get("projectRefs")).stream())
              .map(r -> text(r, "standardId") + ":" + text(r, "itemId"))
              .toList();
      check(
          rows(a.get("projectRefs")).stream()
              .allMatch(r -> allRefs.contains(text(r, "standardId") + ":" + text(r, "itemId"))),
          path + "projectRefs",
          "projects");
    }
    for (String day : strings(plan.get("workdays")))
      try {
        LocalDate.parse(day);
      } catch (Exception ex) {
        throw new FieldValidationException("workdays", MessageRef.of("daily.error.date"));
      }
    // The roster references a shift belonging to a specific arrangement, never an unrelated tenant
    // user pool.
    var slotIds =
        groups.stream()
            .filter(a -> text(a, "frequency").equals("shift"))
            .flatMap(a -> rows(a.get("slots")).stream())
            .map(s -> text(s, "id"))
            .toList();
    for (var r : rows(plan.get("roster"))) {
      check(slotIds.contains(text(r, "shiftId")), "roster", "roster");
      try {
        LocalDate.parse(text(r, "date"));
      } catch (Exception ex) {
        throw new FieldValidationException("roster", MessageRef.of("daily.error.date"));
      }
      validation.users(tenant, r.get("users"), true);
    }
    plan.put("onlyAbnormal", false);
    plan.put("skipUnstaffed", false);
    plan.put("personMode", "指定负责人");
  }

  public List<Map<String, Object>> details(Map<String, Object> plan, Map<String, Object> a) {
    var selected = ids(a.get("equipmentIds"));
    var refs = rows(a.get("projectRefs"));
    return rows(plan.get("details")).stream()
        .filter(d -> selected.isEmpty() || selected.contains(number(d.get("equipmentId"))))
        .map(
            d -> {
              var copy = new LinkedHashMap<String, Object>(d);
              if (!refs.isEmpty())
                copy.put(
                    "projectRefs",
                    rows(d.get("projectRefs")).stream()
                        .filter(
                            r ->
                                refs.stream()
                                    .anyMatch(
                                        p ->
                                            text(p, "standardId").equals(text(r, "standardId"))
                                                && text(p, "itemId").equals(text(r, "itemId"))))
                        .toList());
              return (Map<String, Object>) copy;
            })
        .toList();
  }

  public List<Long> owners(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> a,
      Map<String, Object> detail,
      LocalDate date,
      String shift) {
    List<Long> candidates;
    if (!ids(detail.get("owners")).isEmpty()) candidates = ids(detail.get("owners"));
    else
      candidates =
          switch (text(a, "personMode")) {
            case "team" ->
                ((Map<Long, List<Long>>)
                        plan.computeIfAbsent("_teams", ignored -> new HashMap<Long, List<Long>>()))
                    .computeIfAbsent(
                        number(a.get("teamId")), id -> repository.teamMembers(tenant, id));
            case "roster" ->
                rows(plan.get("roster")).stream()
                    .filter(
                        r ->
                            text(r, "date").equals(date.toString())
                                && text(r, "shiftId").equals(shift))
                    .flatMap(r -> ids(r.get("users")).stream())
                    .distinct()
                    .toList();
            default -> ids(a.get("owners"));
          };
    var active =
        (Set<Long>)
            plan.computeIfAbsent(
                "_activeIds",
                ignored ->
                    repository.activePeople(tenant).stream()
                        .map(r -> number(r.get("id")))
                        .collect(java.util.stream.Collectors.toSet()));
    return candidates.stream().filter(active::contains).distinct().toList();
  }

  public List<Map<String, Object>> teams() {
    var scope = permissions.inspection("daily-plans", "查看");
    return repository.teams(auth.tenantId()).stream()
        .filter(
            t ->
                scope.matches(
                    permissions.companyOfOrganization(nullable(t.get("departmentId"))),
                    nullable(t.get("departmentId")),
                    nullable(t.get("creatorId")),
                    auth.userId()))
        .map(
            t -> {
              t.put("id", text(t, "id"));
              t.put(
                  "members",
                  repository.teamMembers(auth.tenantId(), number(t.get("id"))).stream()
                      .map(Object::toString)
                      .toList());
              return t;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> createTeam(Map<String, Object> body) {
    permissions.require("inspectionDailyPlan", "新增");
    long department = number(body.get("departmentId"));
    var scope = permissions.inspection("daily-plans", "新增");
    if (department <= 0
        || !scope.matches(
            permissions.companyOfOrganization(department),
            department,
            auth.userId(),
            auth.userId())) deny();
    check(!text(body, "name").isBlank() && text(body, "name").length() <= 150, "name", "name");
    validation.users(auth.tenantId(), body.get("members"), true);
    long id = IdWorker.getId();
    repository.createTeam(
        auth.tenantId(),
        id,
        text(body, "name"),
        department,
        auth.userId(),
        ids(body.get("members")));
    return Map.of(
        "id",
        Long.toString(id),
        "name",
        text(body, "name"),
        "members",
        strings(body.get("members")));
  }

  @Transactional
  public Map<String, Object> updateTeam(long id, Map<String, Object> body) {
    var row = repository.lockTeam(auth.tenantId(), id);
    if (row.isEmpty()) deny();
    var scope = permissions.inspection("daily-plans", "编辑");
    if (!scope.matches(
        permissions.companyOfOrganization(nullable(row.get("department_id"))),
        nullable(row.get("department_id")),
        nullable(row.get("created_by")),
        auth.userId())) deny();
    VersionGuard.checkPayload((Number) row.get("row_version"), body);
    check(!text(body, "name").isBlank() && text(body, "name").length() <= 150, "name", "name");
    validation.users(auth.tenantId(), body.get("members"), true);
    repository.updateTeam(auth.tenantId(), id, text(body, "name"), ids(body.get("members")));
    return Map.of(
        "id",
        Long.toString(id),
        "name",
        text(body, "name"),
        "members",
        strings(body.get("members")),
        "rowVersion",
        number(row.get("row_version")) + 1);
  }
}
