package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

/** 关系视图的安排协议；旧整标准安排不返回projectRefs，保留standardId。 */
public record MaintenancePlanDeviceView(
    String id,
    String equipmentId,
    String typeId,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate nextDate,
    Integer period,
    String unit,
    Integer validDays,
    Integer validDuration,
    String validUnit,
    String remark,
    List<String> owners,
    List<String> reviewers,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<MaintenanceProjectReference> projectRefs,
    String standardId) {}
