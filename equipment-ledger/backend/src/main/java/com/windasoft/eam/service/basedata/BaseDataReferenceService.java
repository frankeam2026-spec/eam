package com.windasoft.eam.service.basedata;

import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.repository.basedata.BaseDataRepository;
import java.util.*;
import org.springframework.stereotype.Service;

/** Resolves IDs only. Names are display snapshots and never establish a new reference. */
@Service
public class BaseDataReferenceService {
  public static final String RESOLVED_CODE = "SYS_RESOLVED";
  private final BaseDataRepository repository;

  public BaseDataReferenceService(BaseDataRepository repository) {
    this.repository = repository;
  }

  public BaseItem required(long tenant, Object value, String type, String field, String label) {
    Long id = parse(value, field, label);
    if (id == null)
      throw new FieldValidationException(
          field, com.windasoft.eam.common.MessageRef.of("server.common.message0049", label));
    BaseItem item = repository.reference(tenant, type, id);
    if (item == null || !Integer.valueOf(1).equals(item.getStatus()))
      throw new FieldValidationException(
          field, com.windasoft.eam.common.MessageRef.of("server.common.message0050", label));
    return item;
  }

  public BaseItem optional(long tenant, Object value, String type, String field, String label) {
    return parse(value, field, label) == null ? null : required(tenant, value, type, field, label);
  }

  private Long parse(Object value, String field, String label) {
    String text = Objects.toString(value, "").trim();
    if (text.isEmpty()) return null;
    try {
      long id = Long.parseLong(text);
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new FieldValidationException(
          field, com.windasoft.eam.common.MessageRef.of("server.common.message0051", label));
    }
  }

  public static boolean fixed(BaseItem item) {
    return "PROCESS_RESULT".equals(item.getItemType()) && RESOLVED_CODE.equals(item.getCode());
  }

  public void categories(long tenant, Map<String, Object> data) {
    if (data.get("legacyCategoryNames") instanceof Collection<?> legacy && !legacy.isEmpty())
      throw new FieldValidationException(
          "categoryIds", com.windasoft.eam.common.MessageRef.of("server.common.message0052"));
    var ids = new TreeSet<Long>();
    if (data.get("categoryIds") instanceof Collection<?> values) {
      for (Object value : values) {
        Long id = parse(value, "categoryIds", "设备类别");
        if (id != null) ids.add(id);
      }
    } else if (data.get("categoryNames") instanceof Collection<?> names && !names.isEmpty())
      throw new FieldValidationException(
          "categoryIds", com.windasoft.eam.common.MessageRef.of("server.common.message0053"));
    var names = new ArrayList<String>();
    for (long id : ids)
      names.add(required(tenant, id, "CATEGORY", "categoryIds", "设备类别").getName());
    data.put("categoryIds", ids.stream().map(String::valueOf).toList());
    data.put("categoryNames", names);
    data.put("legacyCategoryNames", List.of());
  }

  public static boolean applicable(
      Map<String, Object> standard,
      Map<String, Object> equipment,
      List<Map<String, Object>> categories) {
    Set<String> equipmentIds = values(standard.get("equipmentIds")),
        ids = values(standard.get("categoryIds"));
    boolean legacy =
        !values(standard.get("legacyCategoryNames")).isEmpty()
            || (!standard.containsKey("categoryIds")
                && !values(standard.get("categoryNames")).isEmpty());
    if (equipmentIds.isEmpty() && ids.isEmpty() && !legacy) return true;
    if (equipmentIds.contains(Objects.toString(equipment.get("id"), ""))) return true;
    String current = Objects.toString(equipment.get("category_id"), "");
    var seen = new HashSet<String>();
    var parents = new HashMap<String, String>();
    for (var row : categories)
      parents.put(Objects.toString(row.get("id"), ""), Objects.toString(row.get("parent_id"), ""));
    while (!current.isBlank() && seen.add(current)) {
      if (ids.contains(current)) return true;
      current = parents.getOrDefault(current, "");
    }
    return false;
  }

  private static Set<String> values(Object value) {
    var result = new HashSet<String>();
    if (value instanceof Collection<?> list)
      for (Object v : list) result.add(Objects.toString(v, ""));
    return result;
  }
}
