package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.business.BusinessField;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessFieldMapper extends BaseMapper<BusinessField> {
  /** 原子upsert保留既有UUID归属和ever_published；标准CRUD不能等价表达此冲突更新语义。 */
  @Insert(
      "INSERT INTO eam_business_field(id,tenant_id,object_key,data_type,source_key)"
          + " VALUES(#{id},#{tenant},#{object},#{type},#{source}) ON DUPLICATE KEY UPDATE"
          + " data_type=VALUES(data_type),source_key=VALUES(source_key)")
  void upsertDefinition(
      @Param("id") String id,
      @Param("tenant") long tenant,
      @Param("object") String object,
      @Param("type") String type,
      @Param("source") String source);
}
