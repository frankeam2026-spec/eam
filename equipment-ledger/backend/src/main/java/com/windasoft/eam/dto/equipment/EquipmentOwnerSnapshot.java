package com.windasoft.eam.dto.equipment;

/** 写入侧使用的负责人编号和历史名称，不直接作为接口响应。 */
public record EquipmentOwnerSnapshot(Long id, String name) {}
