package com.windasoft.eam.common.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Assign cents from the posted allocation; its last usage or return settles the remainder. */
public final class UsageCost {
  private UsageCost() {}

  public static BigDecimal portion(
      BigDecimal issuedQuantity,
      BigDecimal issuedAmount,
      BigDecimal quantity,
      BigDecimal unusedQuantity,
      BigDecimal remainingAmount) {
    if (issuedQuantity.signum() <= 0
        || quantity.signum() <= 0
        || quantity.compareTo(unusedQuantity) > 0
        || remainingAmount.signum() < 0) throw new IllegalArgumentException("用料数量或剩余成本无效");
    return quantity.compareTo(unusedQuantity) == 0
        ? remainingAmount
        : issuedAmount
            .multiply(quantity)
            .divide(issuedQuantity, 2, RoundingMode.HALF_UP)
            .min(remainingAmount);
  }
}
