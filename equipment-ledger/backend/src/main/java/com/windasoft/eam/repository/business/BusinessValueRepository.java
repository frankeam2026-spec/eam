package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.dto.business.BusinessFieldValue;
import com.windasoft.eam.entity.business.BusinessValue;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅维护动态值表；字段可见性、引用资格和完整业务事务由Service/所属业务调用方负责。 */
@Repository
public class BusinessValueRepository {
  private final BusinessValueMapper mapper;

  public BusinessValueRepository(BusinessValueMapper mapper) {
    this.mapper = mapper;
  }

  /** 一次读取授权业务记录的全部扩展值，保留字段、序号顺序，不逐记录查询。 */
  public List<BusinessValue> values(long tenant, String object, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<BusinessValue>()
            .eq(BusinessValue::getTenantId, tenant)
            .eq(BusinessValue::getObjectKey, object)
            .in(BusinessValue::getRecordId, ids)
            .orderByAsc(BusinessValue::getFieldId, BusinessValue::getOrdinalNo));
  }

  public void remove(long tenant, String object, long record) {
    mapper.delete(
        new LambdaQueryWrapper<BusinessValue>()
            .eq(BusinessValue::getTenantId, tenant)
            .eq(BusinessValue::getObjectKey, object)
            .eq(BusinessValue::getRecordId, record));
  }

  /** 按完整字段键替换并从0重排序号；空集合表示清空该字段。调用方必须保持原业务事务， 否则删除后插入失败无法整体回滚；不在仓储另开事务或改变所属业务的锁顺序。 */
  public void replace(
      long tenant, String object, long record, String field, List<BusinessFieldValue> values) {
    mapper.delete(
        new LambdaQueryWrapper<BusinessValue>()
            .eq(BusinessValue::getTenantId, tenant)
            .eq(BusinessValue::getObjectKey, object)
            .eq(BusinessValue::getRecordId, record)
            .eq(BusinessValue::getFieldId, field));
    int ordinal = 0;
    for (var value : values) {
      var row = new BusinessValue();
      row.setTenantId(tenant);
      row.setObjectKey(object);
      row.setRecordId(record);
      row.setFieldId(field);
      row.setOrdinalNo(ordinal++);
      row.setTextValue(value.textValue());
      row.setNumberValue(value.numberValue());
      row.setDateValue(value.dateValue());
      row.setDatetimeValue(value.datetimeValue());
      row.setBoolValue(value.boolValue());
      row.setReferenceId(value.referenceId());
      row.setReferenceName(value.referenceName());
      mapper.insert(row);
    }
  }
}
