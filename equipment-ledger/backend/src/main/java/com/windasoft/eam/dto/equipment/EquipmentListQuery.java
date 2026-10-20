package com.windasoft.eam.dto.equipment;

import java.time.LocalDate;

public record EquipmentListQuery(
    long page,
    long size,
    String keyword,
    String category,
    String state,
    String department,
    String company,
    String model,
    String serial,
    String owner,
    String brand,
    String useState,
    String source,
    LocalDate acquiredFrom,
    LocalDate acquiredTo,
    LocalDate warrantyBefore,
    String sortBy,
    String direction,
    String advanced,
    String scopeType,
    String scopeIds,
    String scopeCompanyId) {
  public EquipmentListQuery at(long page, long size) {
    return new EquipmentListQuery(
        page,
        size,
        keyword,
        category,
        state,
        department,
        company,
        model,
        serial,
        owner,
        brand,
        useState,
        source,
        acquiredFrom,
        acquiredTo,
        warrantyBefore,
        sortBy,
        direction,
        advanced,
        scopeType,
        scopeIds,
        scopeCompanyId);
  }
}
