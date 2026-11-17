package com.windasoft.eam.service.basedata;

import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import java.util.*;
import org.springframework.stereotype.Service;

/** 基础资料引用按真实ID与租户取得共享锁；名称仅供历史展示，不能替代关联校验。 */
@Service
public class BaseDataReferenceService {
  public static final String RESOLVED_CODE = "SYS_RESOLVED";
  private final BaseItemRepository repository;

  public BaseDataReferenceService(BaseItemRepository repository) {
    this.repository = repository;
  }

  public BaseItem required(long tenant, Object value, String type, String field, String label) {
    Long id = parse(value, field, label);
    if (id == null) {
      throw new FieldValidationException(field, MessageRef.of("server.common.message0049", label));
    }
    BaseItem item = repository.reference(tenant, type, id);
    if (item == null || !Integer.valueOf(1).equals(item.getStatus())) {
      throw new FieldValidationException(field, MessageRef.of("server.common.message0050", label));
    }
    return item;
  }

  public BaseItem optional(long tenant, Object value, String type, String field, String label) {
    return parse(value, field, label) == null ? null : required(tenant, value, type, field, label);
  }

  private Long parse(Object value, String field, String label) {
    String text = Objects.toString(value, "").trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      long id = Long.parseLong(text);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException e) {
      throw new FieldValidationException(field, MessageRef.of("server.common.message0051", label));
    }
  }

  public static boolean fixed(BaseItem item) {
    return "PROCESS_RESULT".equals(item.getItemType()) && RESOLVED_CODE.equals(item.getCode());
  }

  public void categories(long tenant, Map<String, Object> data) {
    boolean legacy =
        data.get("legacyCategoryNames") instanceof Collection<?> values && !values.isEmpty();
    boolean named = data.get("categoryNames") instanceof Collection<?> values && !values.isEmpty();
    validateCategorySelection(data.get("categoryIds") instanceof Collection<?>, named, legacy);
    List<Long> selected = null;
    if (data.get("categoryIds") instanceof Collection<?> values) {
      selected = new ArrayList<>();
      for (Object value : values) {
        Long id = parse(value, "categoryIds", "设备类别");
        if (id != null) {
          selected.add(id);
        }
      }
    }
    var items = categories(tenant, selected, named, legacy);
    data.put("categoryIds", items.stream().map(item -> Long.toString(item.getId())).toList());
    data.put("categoryNames", items.stream().map(BaseItem::getName).toList());
    data.put("legacyCategoryNames", List.of());
  }

  /** 新标准按真实分类ID选择；保留旧命名分类拒绝、排序去重和逐分类共享锁顺序。 */
  public List<BaseItem> categories(
      long tenant, List<Long> selected, boolean named, boolean legacy) {
    validateCategorySelection(selected != null, named, legacy);
    var ids = new TreeSet<Long>();
    if (selected != null) {
      for (Long value : selected) {
        Long id = parse(value, "categoryIds", "设备类别");
        if (id != null) {
          ids.add(id);
        }
      }
    }
    var result = new ArrayList<BaseItem>();
    for (long id : ids) {
      result.add(required(tenant, id, BaseDataType.CATEGORY.code(), "categoryIds", "设备类别"));
    }
    return result;
  }

  private void validateCategorySelection(boolean selected, boolean named, boolean legacy) {
    if (legacy) {
      throw new FieldValidationException("categoryIds", MessageRef.of("server.common.message0052"));
    }
    if (!selected && named) {
      throw new FieldValidationException("categoryIds", MessageRef.of("server.common.message0053"));
    }
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
    var parents = new HashMap<String, String>();
    for (var row : categories) {
      parents.put(Objects.toString(row.get("id"), ""), Objects.toString(row.get("parent_id"), ""));
    }
    return applicable(
        equipmentIds,
        ids,
        legacy,
        Objects.toString(equipment.get("id"), ""),
        Objects.toString(equipment.get("category_id"), ""),
        parents);
  }

  /** 类型化调用沿用历史适用性：显式设备命中或类别祖先命中；未解析的旧名称不能当作无限制。 */
  public static boolean applicable(
      Collection<String> equipmentIds,
      Collection<String> categoryIds,
      boolean legacy,
      Long equipmentId,
      Long categoryId,
      List<BaseItem> categories) {
    var parents = new HashMap<String, String>();
    for (var row : categories) {
      parents.put(Long.toString(row.getId()), Objects.toString(row.getParentId(), ""));
    }
    return applicable(
        new HashSet<>(equipmentIds),
        new HashSet<>(categoryIds),
        legacy,
        Objects.toString(equipmentId, ""),
        Objects.toString(categoryId, ""),
        parents);
  }

  private static boolean applicable(
      Set<String> equipmentIds,
      Set<String> ids,
      boolean legacy,
      String equipmentId,
      String categoryId,
      Map<String, String> parents) {
    if (equipmentIds.isEmpty() && ids.isEmpty() && !legacy) {
      return true;
    }
    if (equipmentIds.contains(equipmentId)) {
      return true;
    }
    String current = categoryId;
    var seen = new HashSet<String>();
    while (!current.isBlank() && seen.add(current)) {
      if (ids.contains(current)) {
        return true;
      }
      current = parents.getOrDefault(current, "");
    }
    return false;
  }

  private static Set<String> values(Object value) {
    var result = new HashSet<String>();
    if (value instanceof Collection<?> list) {
      for (Object v : list) {
        result.add(Objects.toString(v, ""));
      }
    }
    return result;
  }
}
