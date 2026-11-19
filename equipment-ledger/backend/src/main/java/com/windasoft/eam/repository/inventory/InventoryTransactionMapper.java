package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.inventory.InventoryTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {}
