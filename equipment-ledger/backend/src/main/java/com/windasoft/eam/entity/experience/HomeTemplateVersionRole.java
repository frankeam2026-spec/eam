package com.windasoft.eam.entity.experience;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_home_template_version_role的持久化字段；画布为既有受校验JSON，元数据和关联保持结构化。 */
@Data
@TableName("eam_home_template_version_role")
public class HomeTemplateVersionRole {
  private Long tenantId;
  private Long templateId;
  private Integer version;
  private Long roleId;
  private Integer sortNo;
}
