package com.windasoft.eam.dto.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EquipmentForm(
    @Size(max = 32, message = "{server.equipment.message0002}") String code,
    @NotBlank(message = "{server.equipment.message0003}") String name,
    String category,
    String model,
    String serial,
    String company,
    String department,
    String owner,
    String brand,
    @NotBlank(message = "{server.equipment.message0004}") String state,
    String useState,
    String source,
    LocalDate acquiredAt,
    BigDecimal amount,
    LocalDate warranty,
    String remark,
    String assetNo,
    String tagCode,
    String supplier,
    String unit,
    LocalDate purchaseDate,
    LocalDate inServiceDate,
    LocalDate retirementDate,
    String level,
    String functionLocation,
    String specialEquipment,
    String metrologyEquipment,
    String depreciationEnabled,
    String technicalParams,
    String imageName,
    String imageFileId,
    JsonNode images,
    JsonNode documents,
    JsonNode children,
    JsonNode relatedSpares,
    LocalDate calibrationDate,
    LocalDate calibrationValidUntil,
    @Size(max = 100) String inspectorName,
    @NotNull(message = "{ui.equipment.text0099}")
        @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long departmentOrgId,
    @NotNull(message = "{server.equipment.message0005}")
        @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long locationId,
    @NotNull(message = "{ui.equipment.text0084}")
        @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long categoryId,
    @NotNull(message = "{server.equipment.message0006}")
        @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long unitId,
    @NotNull(message = "{server.equipment.message0007}")
        @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long useStateId,
    @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long sourceId,
    @Positive(message = "{server.inventory.message0024}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long ownerId,
    @Size(max = 50, message = "{server.equipment.message0001}")
        @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
        List<
                @NotNull(message = "{server.inventory.message0024}")
                @Positive(message = "{server.inventory.message0024}") Long>
            ownerIds,
    JsonNode customFields,
    Integer customSchemaVersion) {
  public EquipmentForm {
    // 保留未传与空集合的区别；空元素由字段校验定位，避免复制时抛出空指针。
    ownerIds =
        ownerIds == null
            ? null
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(ownerIds));
  }
}
