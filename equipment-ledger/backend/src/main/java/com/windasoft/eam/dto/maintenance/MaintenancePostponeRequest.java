package com.windasoft.eam.dto.maintenance;

import java.time.LocalDate;
import java.util.List;

/** 整批延期沿用全有或全无事务；日期是业务日期，不转换为带时区时间。 */
public record MaintenancePostponeRequest(
    List<MaintenanceTaskTarget> tasks, LocalDate endDate, String reason) {}
