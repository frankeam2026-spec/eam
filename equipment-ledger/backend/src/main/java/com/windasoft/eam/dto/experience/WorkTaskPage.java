package com.windasoft.eam.dto.experience;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record WorkTaskPage(List<Row> data, long total, Map<String, Long> moduleCounts) {
  public record Row(
      String id,
      String module,
      String code,
      String name,
      String status,
      String owner,
      String ownerId,
      String creatorId,
      String time,
      String due,
      String departmentId,
      String companyId,
      String url) {

    // 工作台与消息列表共用展示组件，保留组件依赖的历史字段。
    @JsonProperty("orderId")
    public String orderId() {
      return id;
    }

    @JsonProperty("orderNo")
    public String orderNo() {
      return code;
    }

    @JsonProperty("equipmentName")
    public String equipmentName() {
      return name;
    }

    @JsonProperty("kind")
    public String kind() {
      return status;
    }

    @JsonProperty("title")
    public String title() {
      return name;
    }

    @JsonProperty("todo")
    public int todo() {
      return 0;
    }

    @JsonProperty("done")
    public int done() {
      return 0;
    }

    @JsonProperty("isRead")
    public int isRead() {
      return 1;
    }
  }

  public record ScopeChoices(List<Choice> companies, List<Choice> departments) {}

  public record Choice(String id, String name) {}
}
