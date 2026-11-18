package com.windasoft.eam.dto.knowledge;

import java.util.List;

/** 保留现有total/data格式，不迁移成其他分页协议。 */
public record AiCallsView(long total, List<AiCallView> data) {}
