package com.windasoft.eam.dto.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** 编辑输入与只读定义分离：新建允许省略版本，修改必须明确携带完整整数版本。 */
public record WorkflowSaveRequest(
    String id,
    @PositiveOrZero @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion,
    String name,
    String businessType,
    String companyId,
    String description,
    String versionId,
    String state,
    List<WorkflowDefinition.Node> nodes,
    String diagramXml) {
  @JsonIgnore
  @AssertTrue(message = "{server.common.message0018}")
  public boolean isVersionPresentForUpdate() {
    return id == null || rowVersion != null;
  }

  public WorkflowDefinition toDefinition() {
    return new WorkflowDefinition(
        id,
        rowVersion == null ? 0L : rowVersion,
        name,
        businessType,
        companyId,
        description,
        versionId,
        state,
        nodes,
        diagramXml);
  }
}
