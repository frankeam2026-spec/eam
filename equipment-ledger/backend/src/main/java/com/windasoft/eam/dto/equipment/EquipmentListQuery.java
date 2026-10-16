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
    String direction) {}
