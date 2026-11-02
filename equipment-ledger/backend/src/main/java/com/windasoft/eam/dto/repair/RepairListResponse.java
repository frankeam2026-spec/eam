package com.windasoft.eam.dto.repair;

import com.windasoft.eam.dto.common.PageResult;
import java.util.List;
import java.util.Map;

/** 保持维修列表额外队列计数；counts的键限定为RepairQueue编码。 */
public record RepairListResponse(
    List<RepairOrderView> data, PageResult.Page page, Map<String, Long> counts) {}
