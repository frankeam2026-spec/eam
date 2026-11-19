package com.windasoft.eam.dto.inventory;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.sql.Date;
import java.time.LocalDateTime;

/** 四类库存查询共用物料和仓位字段，保持原接口的扁平字段及十进制字符串。 */
public sealed interface InventoryQueryRow {
  record Stock(
      String warehouseId,
      String materialCode,
      String materialName,
      String model,
      String unit,
      String companyName,
      String warehouseName,
      String binName,
      String batchCode,
      String supplierBatch,
      Date productionDate,
      Date expiryDate) {}

  record Source(String sourceCode, String sourceId, String sourceKind) {}

  // 整组缺失表示无权查看；有权限时保留金额未知的 null 字段。
  record BalanceCost(String remainingCost) {}

  record LayerCost(String unitCost, String remainingCost) {}

  record Amount(String amount) {}

  record Balance(
      String id,
      int rowVersion,
      String quantity,
      String reservedQuantity,
      String stockStatus,
      String availableQuantity,
      String expiryStatus,
      @JsonUnwrapped Stock stock,
      @JsonUnwrapped BalanceCost cost)
      implements InventoryQueryRow {}

  record Layer(
      String id,
      String sourceLineId,
      LocalDateTime receivedAt,
      String quantity,
      String remainingQuantity,
      String costStatus,
      String currency,
      @JsonUnwrapped Source source,
      @JsonUnwrapped Stock stock,
      @JsonUnwrapped LayerCost cost)
      implements InventoryQueryRow {}

  record Transaction(
      String id,
      String kind,
      String quantity,
      LocalDateTime createdAt,
      String actorName,
      @JsonUnwrapped Source source,
      @JsonUnwrapped Stock stock,
      @JsonUnwrapped Amount cost)
      implements InventoryQueryRow {}

  record Transit(
      String id,
      String quantity,
      String targetWarehouseName,
      LocalDateTime createdAt,
      @JsonUnwrapped Source source,
      @JsonUnwrapped Stock stock,
      @JsonUnwrapped Amount cost)
      implements InventoryQueryRow {}
}
