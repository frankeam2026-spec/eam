package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.windasoft.eam.dto.business.BusinessChoices.LockedOption;
import com.windasoft.eam.dto.business.BusinessChoices.StoredChoice;
import com.windasoft.eam.entity.business.BusinessOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusinessOptionMapper extends BaseMapper<BusinessOption> {
  /** 子项和字典均需同租户联查；仅查子项会让停用字典下的选项仍可选。 */
  @Select(
      """
      <script>
      SELECT o.id,o.name,o.enabled,o.sort_no sortOrder FROM eam_business_option o
      JOIN eam_business_dictionary d ON d.id=o.dictionary_id AND d.tenant_id=o.tenant_id
      WHERE o.tenant_id=#{tenant} AND o.dictionary_id=#{dictionary}
      <if test="!inactive">AND o.enabled=1 AND d.enabled=1</if>
      AND LOWER(o.name) LIKE #{pattern} ESCAPE '!' ORDER BY sortOrder,o.id
      </script>
      """)
  IPage<StoredChoice> choices(
      IPage<StoredChoice> page,
      @Param("tenant") long tenant,
      @Param("dictionary") String dictionary,
      @Param("pattern") String pattern,
      @Param("inactive") boolean inactive);

  /** 在调用方业务事务内取得父子行共同FOR SHARE，保持校验至业务提交间的状态稳定。 */
  @Select(
      """
      SELECT o.id,o.name,(o.enabled=1 AND d.enabled=1) enabled FROM eam_business_option o
      JOIN eam_business_dictionary d ON d.id=o.dictionary_id AND d.tenant_id=o.tenant_id
      WHERE o.tenant_id=#{tenant} AND o.dictionary_id=#{dictionary} AND o.id=#{id} FOR SHARE
      """)
  LockedOption locked(
      @Param("tenant") long tenant, @Param("dictionary") String dictionary, @Param("id") String id);
}
