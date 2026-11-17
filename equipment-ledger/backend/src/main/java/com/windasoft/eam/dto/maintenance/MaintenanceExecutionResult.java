package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 执行关系数据的固定模型；保留历史项目复合键，日期不增加时区转换。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenanceExecutionResult {
  private String description;
  private LocalDateTime startedAt;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private List<Item> items;
  private List<MaintenanceFileReference> attachments;
  private List<Long> collaborators;
  private Acceptance acceptance;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    private String id;
    private String value;
    private boolean abnormal;
    private boolean resolved;
    private String measure;
    private String remark;
    private boolean implicitNormal;
    private boolean skipped;
    private List<MaintenanceFileReference> images;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Acceptance {
    private String decision;
    private String description;
    private BigDecimal score;
    private Long reviewerId;
    private String reviewer;
    private LocalDateTime reviewedAt;
    private List<MaintenanceFileReference> attachments;
  }
}
