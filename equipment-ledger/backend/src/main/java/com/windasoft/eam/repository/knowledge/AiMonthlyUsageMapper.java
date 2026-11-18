package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.knowledge.AiMonthlyUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiMonthlyUsageMapper extends BaseMapper<AiMonthlyUsage> {
  /** 账期复合键并发初始化不能由先查后插替代。 */
  @Insert(
      "INSERT IGNORE INTO eam_ai_monthly_usage(tenant_id,billing_month) VALUES(#{tenant},#{month})")
  int ensure(@Param("tenant") long tenant, @Param("month") String month);
}
