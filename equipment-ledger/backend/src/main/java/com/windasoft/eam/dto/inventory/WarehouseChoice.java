package com.windasoft.eam.dto.inventory;

/** 仓库候选沿用id/name协议，名称由原公司及仓库名称组合，不暴露额外库存信息。 */
public record WarehouseChoice(String id, String name) {}
