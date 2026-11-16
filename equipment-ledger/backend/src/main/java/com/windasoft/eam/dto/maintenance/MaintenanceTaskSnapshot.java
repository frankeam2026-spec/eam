package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import java.util.List;
import lombok.Data;

/** 从真实历史快照读取办理所需事实；不重新序列化为历史文档，避免丢失旧版本的其他字段或改变缺省值。 人员由任务关系视图覆盖，不从原始snapshot列另建第二份权威。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenanceTaskSnapshot {
  private List<Long> owners;
  private List<Long> reviewers;
  private List<Item> items;
  private boolean scanStart;
  private String equipmentCode;
  private String equipmentName;
  private String calculation;
  private String reviewMode;
  private Cycle cycle;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Cycle {
    @JsonDeserialize(using = ExactLongDeserializer.class)
    private long period;

    private String unit;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    private String id;
    private String name;
    private String valueType;
    private String lower;
    private String upper;
    private boolean required;
    private boolean photoRequired;
    private List<String> options;
    private List<String> normalOptions;
  }
}
