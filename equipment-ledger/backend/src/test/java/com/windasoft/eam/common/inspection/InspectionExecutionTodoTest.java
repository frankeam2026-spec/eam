package com.windasoft.eam.common.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 通知资格的时间边界不依赖运行时钟，也不把可补检与超期不可执行混为一谈。 */
class InspectionExecutionTodoTest {
  @ParameterizedTest
  @CsvSource({
    "PENDING,-60,60,false,true",
    "PENDING,0,0,false,true",
    "PENDING,-60,-1,false,false",
    "PENDING,-60,-1,true,true",
    "EXPIRED,-60,-1,false,false",
    "EXPIRED,-60,-1,true,true",
    "NOT_STARTED,60,120,false,false",
    "EXEMPT,-60,60,true,false",
    "EXPIRED_CANCELLED,-60,-1,true,false",
    "COMPLETED,-60,60,true,false"
  })
  void honorsStatusDeadlineAndLateExecution(
      InspectionTaskStatus status,
      int dueSeconds,
      int expirySeconds,
      boolean allowLate,
      boolean expected) {
    var at = LocalDateTime.of(2026, 11, 11, 8, 0);
    assertEquals(
        expected,
        InspectionSupport.executionTodoEligible(
            status.code(),
            at.plusSeconds(dueSeconds),
            at.plusSeconds(expirySeconds),
            allowLate,
            at));
  }
}
