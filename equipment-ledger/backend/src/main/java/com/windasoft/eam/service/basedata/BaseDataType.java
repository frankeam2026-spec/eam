package com.windasoft.eam.service.basedata;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stable API resource names and database item types; not tenant-configurable data. */
public enum BaseDataType {
  CATEGORY("categories", "CATEGORY", "category", "CAT", true, "category_id"),
  MATERIAL_CATEGORY("material-categories", "MATERIAL_CATEGORY", "base", "MC", true, null),
  UNIT("units", "UNIT", "base", "UNIT", false, "unit_id"),
  LOCATION("locations", "LOCATION", "location", "LOC", true, "function_location_id"),
  USE_STATUS("use-statuses", "USE_STATUS", "base", "USE", false, "use_status_id"),
  SOURCE("sources", "SOURCE", "base", "SRC", false, "source_id"),
  FAULT_TYPE("fault-types", "FAULT_TYPE", "base", "FT", true, null),
  PROCESS_RESULT("processing-results", "PROCESS_RESULT", "base", "PR", false, null),
  FAULT_CAUSE("fault-causes", "FAULT_CAUSE", "base", "FC", false, null);

  private static final Map<String, BaseDataType> BY_RESOURCE =
      Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
          BaseDataType::resource, Function.identity()));

  private final String resource;
  private final String code;
  private final String menu;
  private final String prefix;
  private final boolean hierarchical;
  private final String equipmentReferenceColumn;

  BaseDataType(String resource, String code, String menu, String prefix,
      boolean hierarchical, String equipmentReferenceColumn) {
    this.resource = resource;
    this.code = code;
    this.menu = menu;
    this.prefix = prefix;
    this.hierarchical = hierarchical;
    this.equipmentReferenceColumn = equipmentReferenceColumn;
  }

  public String resource() { return resource; }
  public String code() { return code; }
  public String menu() { return menu; }
  public String prefix() { return prefix; }
  public boolean hierarchical() { return hierarchical; }
  public String equipmentReferenceColumn() { return equipmentReferenceColumn; }

  public static BaseDataType fromResource(String resource) {
    var type = BY_RESOURCE.get(resource);
    if (type == null)
      throw new LocalizedArgumentException("server.common.message0042");
    return type;
  }
}
