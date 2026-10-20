package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.service.business.BusinessConfigService;
import java.io.PrintWriter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceCsvService {
  private final MaintenanceListService lists;
  private final BusinessConfigService configs;
  private final ObjectMapper json;

  public MaintenanceCsvService(
      MaintenanceListService lists, BusinessConfigService configs, ObjectMapper json) {
    this.lists = lists;
    this.configs = configs;
    this.json = json;
  }

  public static String cell(Object value) {
    return com.windasoft.eam.service.common.CsvCells.format(value);
  }

  @Transactional(readOnly = true)
  public void write(
      String kind,
      ListQuery query,
      String requested,
      java.util.function.Supplier<PrintWriter> output) {
    var labels = new LinkedHashMap<String, String>();
    String[] base =
        kind.equals("tasks")
            ? new String[] {
              "code",
              com.windasoft.eam.common.MessageContext.text("output.print.columns.task"),
              "planName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0314"),
              "equipmentName",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.equipmentName"),
              "equipmentCode",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.equipmentCode"),
              "typeName",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0008"),
              "status",
              com.windasoft.eam.common.MessageContext.text("language.manager.status"),
              "department",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.department"),
              "owner",
              com.windasoft.eam.common.MessageContext.text("output.equipment.field.owner"),
              "dueDate",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3197"),
              "expiresDate",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0856"),
              "actualStartAt",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3198"),
              "actualEndAt",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3199"),
              "createdAt",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text1528")
            }
            : new String[] {
              "code",
              kind.equals("plans")
                  ? com.windasoft.eam.common.MessageContext.text("ui.equipment.text0311")
                  : com.windasoft.eam.common.MessageContext.text("ui.equipment.text0310"),
              "name",
              kind.equals("plans")
                  ? com.windasoft.eam.common.MessageContext.text("ui.equipment.text0314")
                  : com.windasoft.eam.common.MessageContext.text("ui.equipment.text0313"),
              "department",
              com.windasoft.eam.common.MessageContext.text("output.repair.field.department"),
              "equipmentCount",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3200"),
              "enabledLabel",
              com.windasoft.eam.common.MessageContext.text("language.manager.status"),
              "calculation",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3201"),
              "itemCount",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3202"),
              "createdAt",
              com.windasoft.eam.common.MessageContext.text("output.maintenance.label3203"),
              "remark",
              com.windasoft.eam.common.MessageContext.text("ui.equipment.text0177")
            };
    for (int i = 0; i < base.length; i += 2) labels.put(base[i], base[i + 1]);
    String object =
        kind.equals("tasks")
            ? "maintenance_task"
            : kind.equals("plans") ? "maintenance_plan" : null;
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
                        key -> {
                          Object value =
                              key.startsWith("custom:")
                                      && row.get("customFieldLabels") instanceof Map<?, ?> extra
                                  ? extra.get(key.substring(7))
                                  : key.equals("status")
                                      ? com.windasoft.eam.common.SystemEnumLabels.display(
                                          "status", row.get(key))
                                      : row.get(key);
                          return cell(value);
                        })
                    .toList()));
      if ((long) number * 200 >= first.page().total()) break;
      page = lists.query(kind, query.at(number + 1, 200), true);
    }
  }
}
