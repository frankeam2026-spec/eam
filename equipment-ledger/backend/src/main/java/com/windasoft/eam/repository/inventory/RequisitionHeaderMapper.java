package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.inventory.RequisitionHeader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RequisitionHeaderMapper extends BaseMapper<RequisitionHeader> {
  // Legacy inventory actions still write through JDBC in the same transaction. Never reuse a
  // session-cached header after those writes, including within an enclosing workflow transaction.
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(
      """
      <script>
      SELECT id,tenant_id,code,company_id,warehouse_id,task_kind,task_id,task_code,
             applicant_id,applicant_name,remark,status,row_version,created_at,deleted,approval_gate
      FROM inv_requisition WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0
      <if test="lock">FOR UPDATE</if>
      </script>
      """)
  RequisitionHeader selectCurrent(
      @Param("tenant") long tenant, @Param("id") long id, @Param("lock") boolean lock);
}
