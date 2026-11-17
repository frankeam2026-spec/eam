package com.windasoft.eam.repository.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.workflow.WorkflowActorService;
import com.windasoft.eam.service.workflow.WorkflowDefinitionValidator;
import com.windasoft.eam.service.workflow.WorkflowDraftService;
import com.windasoft.eam.service.workflow.WorkflowScriptRunner;
import com.windasoft.eam.service.workflow.WorkflowTemplateService;
import com.windasoft.eam.service.workflow.WorkflowVariables;
import java.util.Set;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

class WorkflowPaginationTest {
  @Test
  void offsetUsesLongArithmeticForEveryAcceptedIntegerPage() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    var repository =
        new WorkflowRepository(
            db,
            mock(WorkflowTemplateRepository.class),
            mock(WorkflowVersionRepository.class),
            mock(WorkflowNodeRepository.class),
            mock(WorkflowNodeUserRepository.class),
            mock(WorkflowNodeRoleRepository.class),
            mock(WorkflowBranchRepository.class),
            mock(WorkflowNodeScriptRepository.class),
            mock(WorkflowConditionRepository.class));
    repository.list(
        1L, new PermissionScope(true, null, Set.of(), false), "", null, Integer.MAX_VALUE, 100);
    var args = ArgumentCaptor.forClass(Object[].class);
    verify(db).query(anyString(), any(RowMapper.class), args.capture());
    assertEquals(214748364600L, ((Number) args.getValue()[3]).longValue());
  }

  @Test
  void invalidPaginationIsRejectedInsteadOfSilentlyRewritten() {
    var repository = mock(WorkflowRepository.class);
    var service =
        new WorkflowTemplateService(
            repository,
            mock(WorkflowDraftService.class),
            mock(WorkflowActorService.class),
            mock(WorkflowBindingRepository.class),
            mock(WorkflowTemplateRepository.class),
            mock(WorkflowVersionRepository.class),
            mock(AuthContext.class),
            mock(PermissionService.class),
            mock(WorkflowVariables.class),
            mock(WorkflowDefinitionValidator.class),
            mock(ProcessEngine.class),
            mock(WorkflowScriptRunner.class),
            mock(PlatformTransactionManager.class));
    assertThrows(IllegalArgumentException.class, () -> service.list("", null, 0, 20));
    assertThrows(IllegalArgumentException.class, () -> service.list("", null, 1, 21));
  }
}
