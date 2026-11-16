package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.windasoft.eam.dto.common.PageResult;
import java.util.List;

/** 同一路径兼容旧无分页选择和分页列表；分页元数据仍用公共Page类型。 */
public record MaintenanceStandardList(
    List<MaintenanceStandardRow> data,
    @JsonInclude(JsonInclude.Include.NON_NULL) PageResult.Page page) {}
