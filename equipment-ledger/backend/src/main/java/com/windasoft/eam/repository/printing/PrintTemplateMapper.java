package com.windasoft.eam.repository.printing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.printing.PrintTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrintTemplateMapper extends BaseMapper<PrintTemplate> {
  /** 公司名只读联查，避免逐模板读取组织；保存响应仍保留原单表读取语义。 */
  @Select(
      """
      SELECT t.*,o.name company_name FROM eam_print_template t
      LEFT JOIN eam_org_unit o ON o.tenant_id=t.tenant_id AND o.id=t.company_id
      WHERE t.tenant_id=#{tenant} AND t.deleted=0
      ORDER BY t.is_default DESC,t.updated_at DESC,t.id DESC
      """)
  List<PrintTemplate> listed(@Param("tenant") long tenant);
}
