package com.windasoft.eam.dto.imports;

import com.windasoft.eam.dto.business.BusinessFieldValue;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.entity.equipment.Equipment;
import java.util.List;
import java.util.Map;

/** 已通过校验的导入记录，扩展值只含规范化数据；不直接绑定HTTP输入。 */
public record PreparedEquipmentImport(
    int row,
    EquipmentForm form,
    Equipment entity,
    String parentCode,
    Map<String, List<BusinessFieldValue>> custom,
    Map<Long, String> ownerNames) {}
