package com.windasoft.eam.service.inspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.service.business.*;
import java.io.PrintWriter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionCsvService {
  private final InspectionListService lists;
  private final BusinessConfigService configs;
  private final ObjectMapper json;

  public InspectionCsvService(
      InspectionListService lists, BusinessConfigService configs, ObjectMapper json) {
    this.lists = lists;
    this.configs = configs;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public void write(
      String kind,
      ListQuery query,
      String requested,
      java.util.function.Supplier<PrintWriter> output) {
    var labels = new LinkedHashMap<String, String>();
    String[] fields =
        kind.endsWith("tasks")
            ? new String[] {
              "code",
              com.windasoft.eam.common.MessageContext.text("output.print.columns.task"),
              "planName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0314"),
              "shiftName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0365"),
              "shiftDate",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3204"),
              "typeName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0325"),
              "reviewMode",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0429"),
              "status",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3205"),
              "equipmentName",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.equipmentName"),
              "equipmentCount",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3200"),
              "departmentName",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.department"),
              "ownerNames",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0176"),
              "ownerName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0430"),
              "reviewerNames",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0337"),
              "dueAt",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3206"),
              "expiresAt",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text1391"),
              "expected",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3207"),
              "checked",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3208"),
              "missing",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3209"),
              "abnormal",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3210"),
              "skipped",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3211"),
              "startedAt",
              com.windasoft.eam.common.MessageContext.text("component.el.datepicker.startTime"),
              "finishedAt",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3212"),
              "reviewer",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0337"),
              "reviewedAt",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0455"),
              "delayDays",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3213"),
              "workSeconds",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3214"),
              "createdAt",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text1528")
            }
            : new String[] {
              "code",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0137"),
              "name",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0404"),
              "departmentName",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.department"),
              "enabled",
              com.windasoft.eam.common.MessageContext.text("language.manager.status"),
              "typeName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0325"),
              "equipmentCount",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3200"),
              "itemCount",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3202"),
              "shiftCount",
              com.windasoft.eam.common.MessageContext.text("output.inspection.label3215"),
              "startDate",
              com.windasoft.eam.common.MessageContext.text("component.el.datepicker.startDate"),
              "endDate",
              com.windasoft.eam.common.MessageContext.text("component.el.datepicker.endDate"),
              "personMode",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0332"),
              "periodLabel",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0405"),
              "nextAt",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0327"),
              "createdAt",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3203"),
              "remark",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0177")
            };
    for (int i = 0; i < fields.length; i += 2) labels.put(fields[i], fields[i + 1]);
    if(kind.equals("daily-tasks")) {
      labels.put("arrangementName",com.windasoft.eam.common.MessageContext.text("daily.name"));
      labels.put("businessDate",com.windasoft.eam.common.MessageContext.text("daily.businessDate"));
    }
    if(kind.equals("daily-plans"))labels.put("arrangementCount",com.windasoft.eam.common.MessageContext.text("daily.arrangementCount"));
    if (kind.startsWith("daily-")) labels.remove("typeName");
    String object = BusinessObjects.inspection(kind);
    if (object != null)
      for (var field :
          configs.fields(configs.decode(configs.current(object, false).get("published"))).values())
        if (field.path("exportable").asBoolean())
          labels.put("custom:" + field.path("id").asText(), field.path("name").asText());
    List<String> keys;
    try {
      keys =
          requested == null || requested.isBlank()
              ? new ArrayList<>(labels.keySet())
              : json.readValue(
                  requested, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0016");
    }
    if (keys.isEmpty()
        || new HashSet<>(keys).size() != keys.size()
        || !labels.keySet().containsAll(keys))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0016");
    var first = lists.query(kind, query.at(1, 200), true);
    var writer = output.get();
    writer.print('\ufeff');
    writer.println(String.join(",", keys.stream().map(k -> cell(labels.get(k))).toList()));
    var page = first;
    for (int number = 1; ; number++) {
      for (var row : page.data())
        writer.println(
            String.join(
                ",",
                keys.stream()
                    .map(
                        key ->
                            cell(
                                key.startsWith("custom:")
                                        && row.get("customFieldLabels") instanceof Map<?, ?> extra
                                    ? extra.get(key.substring(7))
                                    : key.equals("enabled")
                                        ? (Boolean.TRUE.equals(row.get(key)) ? "启用" : "停用")
                                        : key.equals("status")
                                            ? com.windasoft.eam.common.SystemEnumLabels.display(
                                                "status", row.get(key))
                                            : row.get(key)))
                    .toList()));
      if ((long) number * 200 >= first.page().total()) break;
      page = lists.query(kind, query.at(number + 1, 200), true);
    }
  }

  private String cell(Object value) {
    return com.windasoft.eam.service.common.CsvCells.format(value);
  }
}
