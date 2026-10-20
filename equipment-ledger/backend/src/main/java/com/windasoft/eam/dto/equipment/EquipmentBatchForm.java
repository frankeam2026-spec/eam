package com.windasoft.eam.dto.equipment;

import jakarta.validation.constraints.*;
import java.util.List;

public record EquipmentBatchForm(
    @jakarta.validation.constraints.NotEmpty List<Long> ids,
    @NotBlank String operation,
    String value,
    @Size(max = 50, message = "{server.equipment.message0001}") List<Long> ownerIds) {}
