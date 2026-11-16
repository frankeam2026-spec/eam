package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceBatchAcceptance(
    @JsonDeserialize(using = ExactLongDeserializer.class) Long id,
    @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
    String decision,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long score,
    String description,
    List<MaintenanceFileReference> attachments) {
  public MaintenanceTaskCommand.Accept command() {
    return new MaintenanceTaskCommand.Accept(rowVersion, decision, score, description, attachments);
  }
}
