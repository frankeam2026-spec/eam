package com.windasoft.eam.repository.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 基础字典及功能位置的数据访问；业务校验由 Service 负责。 */
@Repository
public class BaseItemRepository {
  // 沿用设备列表原有IN分批边界，限制单条查询参数规模，不代表业务条数上限。
  private static final int REFERENCE_BATCH_SIZE = 500;
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

  /**
   * 批量读取引用项及全部上级，供设备历史名称和位置路径展示。停用项仍保留，逻辑删除项由 Mapper 排除；
   * 每一层都限定租户，不沿跨租户的父级引用继续读取。已请求ID包含缺失项，防止重复查询和循环父链。 这里只收集记录，位置所属公司、资料类型及路径循环是否应报错仍由调用方判定。
   */
  public Map<Long, BaseItem> findWithAncestors(long tenant, Collection<Long> ids) {
    var result = new HashMap<Long, BaseItem>();
    var requested = new HashSet<Long>();
    var pending = new HashSet<>(ids);
    pending.remove(null);
    while (!pending.isEmpty()) {
      var next = new ArrayList<>(pending);
      requested.addAll(next);
      pending.clear();
      for (int start = 0; start < next.size(); start += REFERENCE_BATCH_SIZE) {
        var batch = next.subList(start, Math.min(start + REFERENCE_BATCH_SIZE, next.size()));
        for (var item :
            mapper.selectList(
                new LambdaQueryWrapper<BaseItem>()
                    .eq(BaseItem::getTenantId, tenant)
                    .in(BaseItem::getId, batch))) {
          result.put(item.getId(), item);
          if (item.getParentId() != null && !requested.contains(item.getParentId())) {
            pending.add(item.getParentId());
          }
        }
      }
    }
    return result;
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
