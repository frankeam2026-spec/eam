package com.windasoft.eam.dto.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** Only the selected relation is replaced; no equipment fields are accepted here. */
public record EquipmentRelationsForm(@NotNull JsonNode rows) {}
