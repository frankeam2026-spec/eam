package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.entity.business.BusinessSchema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** 仅维护当前配置行，版本归档、字段标记及审计由Service在同一事务内协调。 */
@Repository
public class BusinessSchemaRepository {
  private final BusinessSchemaMapper mapper;

  public BusinessSchemaRepository(BusinessSchemaMapper mapper) {
    this.mapper = mapper;
  }

  public BusinessSchema find(long tenant, String object, boolean lock) {
    var query =
        new LambdaQueryWrapper<BusinessSchema>()
            .eq(BusinessSchema::getTenantId, tenant)
            .eq(BusinessSchema::getObjectKey, object);
    if (lock) query.last("FOR UPDATE");
    var row = mapper.selectOne(query);
    if (row == null) throw new LocalizedArgumentException("server.business.message0001");
    return row;
  }

  public void draft(long tenant, String object, String draft, int version, long actor) {
    int changed =
        mapper.update(
            null,
            new LambdaUpdateWrapper<BusinessSchema>()
                .eq(BusinessSchema::getTenantId, tenant)
                .eq(BusinessSchema::getObjectKey, object)
                .eq(BusinessSchema::getRowVersion, version)
                .set(BusinessSchema::getDraft, draft)
                .set(BusinessSchema::getUpdatedBy, actor)
                .setSql("row_version=row_version+1,updated_at=NOW()"));
    if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置已更新，请刷新后重试");
  }

  /** 调用方已持有配置行锁；保留数据库NOW时间及原行版本递增，不引入应用端时钟。 */
  public void published(long tenant, String object, String value, int version, long actor) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<BusinessSchema>()
            .eq(BusinessSchema::getTenantId, tenant)
            .eq(BusinessSchema::getObjectKey, object)
            .set(BusinessSchema::getPublished, value)
            .set(BusinessSchema::getPublishedVersion, version)
            .set(BusinessSchema::getUpdatedBy, actor)
            .setSql("row_version=row_version+1,updated_at=NOW()"));
  }
}
