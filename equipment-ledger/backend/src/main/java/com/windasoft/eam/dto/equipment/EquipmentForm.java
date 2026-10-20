package com.windasoft.eam.dto.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
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
    @jakarta.validation.constraints.NotNull(message = "{ui.equipment.text0099}")
        Long departmentOrgId,
    @jakarta.validation.constraints.NotNull(message = "{server.equipment.message0005}")
        Long locationId,
    @jakarta.validation.constraints.NotNull(message = "{ui.equipment.text0084}") Long categoryId,
    @jakarta.validation.constraints.NotNull(message = "{server.equipment.message0006}") Long unitId,
    @jakarta.validation.constraints.NotNull(message = "{server.equipment.message0007}")
        Long useStateId,
    Long sourceId,
    Long ownerId,
    @Size(max = 50, message = "{server.equipment.message0001}") List<Long> ownerIds,
    JsonNode customFields,
    Integer customSchemaVersion) {}
