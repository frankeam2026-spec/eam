package com.windasoft.eam.common.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UsageCostTest {
  private BigDecimal d(String x) {
    return new BigDecimal(x);
  }

  @Test
  void finalUseSettlesTail() {
    var first = UsageCost.portion(d("3"), d("10.00"), d("1"), d("3"), d("10.00"));
    var second = UsageCost.portion(d("3"), d("10.00"), d("1"), d("2"), d("6.67"));
    var last = UsageCost.portion(d("3"), d("10.00"), d("1"), d("1"), d("3.34"));
    assertEquals(d("3.33"), first);
    assertEquals(d("10.00"), first.add(second).add(last));
  }

  @Test
  void returnSettlesOnlyUnusedCost() {
    assertEquals(d("6.67"), UsageCost.portion(d("3"), d("10.00"), d("2"), d("2"), d("6.67")));
  }

  @Test
  void partialReturnThenUseSettlesPostedBudget() {
    var returned = UsageCost.portion(d("3"), d("10.00"), d("1"), d("3"), d("10.00"));
    var consumed =
        UsageCost.portion(d("3"), d("10.00"), d("2"), d("2"), d("10.00").subtract(returned));
    assertEquals(d("10.00"), returned.add(consumed));
  }

  @Test
  void neverOverdrawsCents() {
    assertEquals(d("0.01"), UsageCost.portion(d("3"), d("0.02"), d("1"), d("2"), d("0.01")));
  }

  @Test
  void rejectsInvalidRemainderAndQuantity() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UsageCost.portion(d("3"), d("10"), d("4"), d("3"), d("10")));
    assertThrows(
        IllegalArgumentException.class,
        () -> UsageCost.portion(d("3"), d("10"), d("1"), d("3"), d("-1")));
  }
}
