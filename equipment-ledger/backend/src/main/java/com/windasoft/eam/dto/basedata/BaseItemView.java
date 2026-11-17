package com.windasoft.eam.dto.basedata;

/** 基础资料和地点沿用同一固定输出；缺少父级或公司以原空字符串表示。 */
public record BaseItemView(
    boolean builtin,
    String companyId,
    String id,
    String code,
    String name,
    String parentId,
    int precision,
    String status,
    int sortNo) {}
