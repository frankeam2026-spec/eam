package com.windasoft.eam.repository.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RepairAttachmentQueryTest {
  @Test
  void eventAttachmentMustMatchIdInsideAttachmentsArray() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "repair-attachment-test"),
        RepairOrder.class);
    var mapper = mock(RepairOrderMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());
    var repository = new RepairOrderRepository(mapper, mock(RepairRelationsRepository.class));

    repository.attachmentOrders(10001L, "01234567-89ab-4cde-8012-3456789abcde");

    // 此处验证 SQL 使用结构化附件字段，而不是事件 JSON 全文模糊检索。
    var captured = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(captured.capture());
    var sql = captured.getValue().getSqlSegment();
    assertTrue(sql.contains("JSON_SEARCH"));
    assertTrue(sql.contains("$.attachments[*].id"));
    assertFalse(sql.contains("details_json LIKE"));
  }
}
