package com.windasoft.eam.repository.repair;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.domain.equipment.EquipmentSpareStatus;
import com.windasoft.eam.domain.repair.LegacyRepairMaterialStatus;
import com.windasoft.eam.domain.repair.RepairCandidateStatus;
import com.windasoft.eam.dto.knowledge.AiQueryLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 仅约束本批已确认业务编码，避免通用禁字面量规则误伤SQL语法、字段名及局部计算。 */
class RepairFixedValuesTest {
  @Test
  void paginationPreservesExistingAssistantContract() {
    for (int size : List.of(20, 50, 100)) {
      assertTrue(AiQueryLimits.acceptsPage(1, size));
      assertTrue(AiQueryLimits.acceptsPage(AiQueryLimits.MAX_PAGE_NUMBER, size));
      assertFalse(AiQueryLimits.acceptsPage(0, size));
      assertFalse(AiQueryLimits.acceptsPage(AiQueryLimits.MAX_PAGE_NUMBER + 1, size));
    }
    for (int size : List.of(-1, 0, 10, 21, 99, 101)) {
      assertFalse(AiQueryLimits.acceptsPage(1, size));
    }
  }

  @Test
  void codesRemainCompatibleAndSemanticallySeparate() {
    assertEquals("待接单", RepairCandidateStatus.PENDING_CLAIM.code());
    assertEquals("已接单", RepairCandidateStatus.CLAIMED.code());
    assertEquals("已由他人接单", RepairCandidateStatus.CLAIMED_BY_OTHER.code());
    assertEquals("待出库", LegacyRepairMaterialStatus.PENDING_ISSUE.code());
    assertEquals("已出库", LegacyRepairMaterialStatus.ISSUED.code());
    assertEquals("已撤回", LegacyRepairMaterialStatus.WITHDRAWN.code());
    assertEquals("在用", EquipmentSpareStatus.INSTALLED.code());
    assertEquals("已换下", EquipmentSpareStatus.REMOVED.code());
  }

  static boolean scatteredCode(String source) {
    return java.util.regex.Pattern.compile("[\"'](?:待接单|已接单|已由他人接单|待出库|已撤回|在用|已换下|报修待修改)[\"']")
        .matcher(source)
        .find();
  }

  @Test
  void guardRejectsBusinessLiteralAndAllowsEnumOrNecessarySyntax() {
    assertTrue(scatteredCode("status='待接单'"));
    assertFalse(scatteredCode("status={0},NOW(),page < 1"));
    assertFalse(scatteredCode("RepairCandidateStatus.PENDING_CLAIM.code()"));
  }

  @Test
  void touchedAccessPathsDoNotReintroduceScatteredCodes() throws Exception {
    for (String path :
        List.of(
            "repair/RepairCandidateRepository.java",
            "repair/RepairInboxRepository.java",
            "repair/RepairMaterialRepository.java",
            "repair/RepairWorkflowMapper.java",
            "repair/RepairQueryRepository.java",
            "equipment/EquipmentSpareMapper.java",
            "equipment/EquipmentSpareRepository.java")) {
      assertFalse(
          scatteredCode(
              Files.readString(Path.of("src/main/java/com/windasoft/eam/repository", path))),
          path);
    }
  }
}
