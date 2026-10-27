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
    for (String field : List.of("weeklyRestDays", "monthlyRestDays")) {
      int max = field.equals("weeklyRestDays") ? 7 : 31;
      check(ids(plan.get(field)).stream().allMatch(day -> day >= 1 && day <= max), field, "restDay");
    }
    var groups = rows(plan.get("arrangements"));
    check(!groups.isEmpty() && groups.size() <= 30, "arrangements", "arrangements");
    var equipment =
        new HashSet<>(
            ids(rows(plan.get("details")).stream().map(d -> d.get("equipmentId")).toList()));
    var activeIds =
        repository.activePeople(tenant).stream()
            .map(u -> number(u.get("id")))
            .collect(java.util.stream.Collectors.toSet());
    var previous =
        planId == null ? List.<Map<String, Object>>of() : repository.load(tenant, planId);
    var keys = new HashSet<Long>();
    for (int i = 0; i < groups.size(); i++) {
      var a = groups.get(i);
      // Arrangements follow their plan; retain true only for older DTO clients.
      a.put("enabled", true);
      String path = "arrangements." + i + ".";
      if (planId == null || text(a, "id").isBlank()) a.put("id", Long.toString(IdWorker.getId()));
      long id = number(a.get("id"));
      check(id > 0 && keys.add(id), path + "name", "arrangements");
      Long owner = repository.ownerPlan(tenant, id);
      check(owner == null || Objects.equals(owner, planId), path + "name", "arrangements");
      check(!text(a, "name").isBlank() && text(a, "name").length() <= 150, path + "name", "name");
      a.put("effectiveFrom", now().toString());
      String f = text(a, "frequency");
      check(Set.of("shift", "slots").contains(f), path + "frequency", "shiftOnly");
      a.put("frequency", "shift");
      // Existing column remains a compatibility mirror, never an independent timing rule.
      a.put("anchorDate", text(plan, "startDate"));
      if (Set.of("shift", "slots").contains(f)) {
        var slots = rows(a.get("slots"));
        check(slots.size() <= 24, path + "slots", "slots");
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
      check(
          Set.of("open", "specified", "team", "roster").contains(mode),
          path + "personMode",
          "people");
      check(
          (mode.equals("open") && text(a, "followUpUserId").isBlank())
              || activeIds.contains(number(a.get("followUpUserId"))),
          path + "followUpUserId",
          "people");
      if (mode.equals("specified"))
        check(!ids(a.get("owners")).isEmpty(), path + "owners", "people");
      check(activeIds.containsAll(ids(a.get("owners"))), path + "owners", "people");
      if (mode.equals("team"))
        check(
            teams().stream().anyMatch(t -> text(t, "id").equals(text(a, "teamId")))
                && !repository.teamMembers(tenant, number(a.get("teamId"))).isEmpty(),
            path + "teamId",
            "people");
      var prior =
          previous.stream()
              .filter(x -> text(x, "id").equals(text(a, "id")))
              .findFirst()
              .orElse(Map.of());
      if (!a.containsKey("regularRoster"))
        a.put("regularRoster", prior.getOrDefault("regularRoster", List.of()));
      if (!a.containsKey("dateRoster"))
        a.put("dateRoster", prior.getOrDefault("dateRoster", List.of()));
      if (!prior.isEmpty() && repository.pendingScheduleTasks(tenant, id)) {
        boolean changed =
            List.of(
                    "intervalCount",
                    "startTime",
                    "endTime",
                    "windowMinutes")
                .stream()
                .anyMatch(
                    field ->
                        !Objects.toString(a.get(field), "")
                            .equals(Objects.toString(prior.get(field), "")));
        var beforeSlots = rows(prior.get("slots"));
        var afterSlots = rows(a.get("slots"));
        changed |=
            beforeSlots.size() != afterSlots.size()
                || beforeSlots.stream()
                    .anyMatch(
                        old ->
                            afterSlots.stream()
                                .noneMatch(
                                    next ->
                                        text(old, "id").equals(text(next, "id"))
                                            && text(old, "start").equals(text(next, "start"))
                                            && text(old, "end").equals(text(next, "end"))));
        check(!changed, path + "slots", "scheduleInUse");
      }
      validateStaffing(a, plan, path, activeIds);
      check(equipment.containsAll(ids(a.get("equipmentIds"))), path + "equipmentIds", "equipment");
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
    return ownersAt(
        tenant, plan, a, detail, date, shift, date.atTime(LocalTime.parse(text(a, "startTime"))));
  }

  public List<Long> ownersAt(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> a,
      Map<String, Object> detail,
      LocalDate date,
      String shift,
      LocalDateTime at) {
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
            case "open" -> List.of();
            case "roster" -> DailyStaffing.resolve(a, date, shift, at);
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

  public boolean open(Map<String, Object> a, Map<String, Object> detail) {
    return text(a, "personMode").equals("open") && ids(detail.get("owners")).isEmpty();
  }

  private void validateStaffing(
      Map<String, Object> a, Map<String, Object> plan, String path, Set<Long> activeIds) {
    for (String property : List.of("regularRoster", "dateRoster")) {
      var entries = rows(a.get(property));
      check(entries.size() <= 3000, path + property, "staffLimit");
      var keys = new HashSet<String>();
      for (int i = 0; i < entries.size(); i++) {
        var r = entries.get(i);
        String field = path + property + "." + i;
        if (text(r, "key").isBlank()) r.put("key", UUID.randomUUID().toString().replace("-", ""));
        check(
            text(r, "key").matches("[a-zA-Z0-9-]{1,32}") && keys.add(text(r, "key")),
            field,
            "staffConflict");
        String slot = text(r, "slotId");
        boolean slots = Set.of("shift", "slots").contains(text(a, "frequency"));
        check(
            slots
                ? rows(a.get("slots")).stream().anyMatch(x -> text(x, "id").equals(slot))
                : slot.isBlank(),
            field,
            "staffSlot");
        r.putIfAbsent("start", "00:00:00");
        r.putIfAbsent("end", "00:00:00");
        if (!slot.isBlank()) {
          r.put("start", "00:00:00");
          r.put("end", "00:00:00");
        }
        try {
          LocalTime.parse(text(r, "start"));
          LocalTime.parse(text(r, "end"));
        } catch (Exception e) {
          check(false, field, "time");
        }
        if (property.equals("dateRoster")) {
          try {
            var date = LocalDate.parse(text(r, "date"));
            check(
                !date.isBefore(LocalDate.parse(text(plan, "startDate")))
                    && !date.isAfter(LocalDate.parse(text(plan, "endDate"))),
                field,
                "date");
          } catch (java.time.format.DateTimeParseException e) {
            check(false, field, "date");
          }
        } else check(number(r.get("weekday")) >= 0 && number(r.get("weekday")) <= 7, field, "days");
        check(
            yes(r.get("unassigned"))
                ? ids(r.get("users")).isEmpty()
                : !ids(r.get("users")).isEmpty(),
            field,
            "staffPeople");
        check(activeIds.containsAll(ids(r.get("users"))), field, "people");
        for (int j = 0; j < i; j++) {
          var other = entries.get(j);
          boolean same =
              property.equals("dateRoster")
                  ? text(r, "date").equals(text(other, "date"))
                  : number(r.get("weekday")) == number(other.get("weekday"));
          check(
              !DailyStaffing.conflict(r, other, property.equals("dateRoster")),
              field,
              "staffConflict");
        }
      }
    }
  }
}
