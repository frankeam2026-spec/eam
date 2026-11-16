package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 编辑器展示字段可回传；类型名称和备件摘要必须由后台重新取得。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceStandardItemRequest(
    @Size(max = 128) String id,
    @Size(max = 64) String code,
    @Size(max = 150) String module,
    @NotBlank(message = "{server.maintenance.message0024}") @Size(max = 200) String name,
    @NotBlank(message = "{server.maintenance.message0024}") String content,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long typeId,
    String valueType,
    String lower,
    String upper,
    Boolean required,
    Boolean photoRequired,
    String remark,
    List<String> options,
    List<String> normalOptions,
    @Valid List<MaintenanceFileReference> references,
    @Valid List<MaintenanceSpareRequirement> spareRequirements) {}
