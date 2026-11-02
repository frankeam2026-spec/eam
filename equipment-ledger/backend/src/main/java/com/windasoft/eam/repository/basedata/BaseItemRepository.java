package com.windasoft.eam.repository.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 基础字典及功能位置的数据访问；业务校验由 Service 负责。 */
@Repository
public class BaseItemRepository {
  private final BaseItemMapper mapper;

  public BaseItemRepository(BaseItemMapper mapper) {
    this.mapper = mapper;
  }

  public BaseItem findForShare(Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BaseItem>().eq(BaseItem::getId, id).last("FOR SHARE"));
  }

  public BaseItem activeForShare(long tenant, String id, String type) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getId, id)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getStatus, 1)
            .last("FOR SHARE"));
  }

  public BaseItem findById(Long id) {
    return mapper.selectById(id);
  }

  public long locationCount(long tenant, Long id) {
    return mapper.selectCount(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, "LOCATION")
            .eq(BaseItem::getCompanyOrgId, id));
  }

  /** 按服务层提供的字典类型读取启用项；类型含义及可选范围由业务服务确定。 */
  public List<BaseItem> activeItemsByType(long tenant, String itemType) {
    return mapper.selectList(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, itemType)
            .eq(BaseItem::getStatus, 1)
            .orderByAsc(BaseItem::getSortNo));
  }
}
