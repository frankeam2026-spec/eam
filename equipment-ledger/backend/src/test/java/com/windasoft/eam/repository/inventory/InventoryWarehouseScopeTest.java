package com.windasoft.eam.repository.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.windasoft.eam.dto.inventory.WarehouseChoice;
import com.windasoft.eam.entity.inventory.Warehouse;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InventoryWarehouseScopeTest {
  @Test
  void warehouseCountUsesTenantAndBoundIdsAndHandlesEmptyInput() {
    var db = mock(JdbcTemplate.class);
    var mapper = mock(WarehouseMapper.class);
    var repository = new WarehouseRepository(mapper, db);
    assertEquals(0L, repository.countIds(7L, List.of()));
    verifyNoInteractions(db, mapper);

    var configuration = new MybatisConfiguration();
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(configuration, "warehouse"), Warehouse.class);
    var condition = ArgumentCaptor.<Wrapper<Warehouse>>captor();
    when(mapper.selectCount(any())).thenReturn(2L);
    assertEquals(2L, repository.countIds(7L, List.of(101L, 102L)));
    verify(mapper).selectCount(condition.capture());
    var wrapper = (LambdaQueryWrapper<Warehouse>) condition.getValue();
    assertTrue(wrapper.getSqlSegment().contains("tenant_id = #{"));
    assertTrue(wrapper.getSqlSegment().contains("id IN (#{"));
    assertEquals(Set.of(7L, 101L, 102L), Set.copyOf(wrapper.getParamNameValuePairs().values()));
  }

  @Test
  void warehouseChoicesRetainsTenantBoundJoinAndNameShape() {
    var db = mock(JdbcTemplate.class);
    var mapper = mock(WarehouseMapper.class);
    var repository = new WarehouseRepository(mapper, db);
    when(db.query(
            anyString(), org.mockito.ArgumentMatchers.<RowMapper<WarehouseChoice>>any(), eq(7L)))
        .thenReturn(List.of(new WarehouseChoice("101", "一厂 / 原料库")));
    assertEquals(List.of(new WarehouseChoice("101", "一厂 / 原料库")), repository.warehouseChoices(7L));
    var sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(db)
        .query(
            sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<WarehouseChoice>>any(), eq(7L));
    assertTrue(sql.getValue().contains("o.tenant_id=w.tenant_id"));
    assertTrue(sql.getValue().contains("w.tenant_id=?"));
    assertTrue(sql.getValue().contains("ORDER BY o.name,w.name"));
  }
}
