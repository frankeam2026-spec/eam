package com.windasoft.eam.repository.repair;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.entity.repair.RepairCollaborator;
import com.windasoft.eam.repository.common.AssistantReadSource;
import com.windasoft.eam.service.repair.RepairQueryService;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

/** 防止通用工作流Mapper重新接管其他对象写入及SQL经Service向AI传递。 */
class RepairDataOwnershipTest {
  interface Wrong {
    @Update("UPDATE eam_repair_event SET action='x'")
    void write();
  }

  interface ReadOnly {
    @Select("SELECT * FROM eam_repair_event e JOIN eam_repair_order r ON r.id=e.order_id")
    void read();
  }

  static boolean readOnly(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .noneMatch(
            m ->
                m.isAnnotationPresent(Update.class)
                    || m.isAnnotationPresent(Insert.class)
                    || m.isAnnotationPresent(Delete.class));
  }

  @Test
  void guardRejectsCrossObjectWriteAndAllowsReadOnlyJoin() {
    assertFalse(readOnly(Wrong.class));
    assertTrue(readOnly(ReadOnly.class));
    assertTrue(readOnly(RepairWorkflowMapper.class));
  }

  @Test
  void workflowRepositoryDoesNotOwnForeignMappersOrWrites() {
    var names =
        Arrays.stream(RepairWorkflowRepository.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();
    for (String n :
        List.of(
            "updateOrder",
            "inbox",
            "claim",
            "install",
            "history",
            "addStock",
            "consume",
            "clearCandidates")) {
      assertFalse(names.contains(n), n);
    }
    for (var field : RepairWorkflowRepository.class.getDeclaredFields()) {
      assertNotEquals(RepairOrderMapper.class, field.getType());
      assertFalse(
          field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class));
    }
  }

  @Test
  void repairServiceCannotReturnSqlSource() {
    for (var m : RepairQueryService.class.getDeclaredMethods()) {
      assertNotEquals(AssistantReadSource.class, m.getReturnType());
    }
  }

  @Test
  void collaboratorKeepsRealCompositeKey() {
    assertEquals(
        "eam_repair_collaborator",
        RepairCollaborator.class
            .getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class)
            .value());
    for (var field : RepairCollaborator.class.getDeclaredFields()) {
      assertFalse(field.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableId.class));
    }
  }
}
