package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 按真实复合键关联，不伪造单列主键。 */
@Data
@TableName("eam_knowledge_case_source")
public class KnowledgeCaseSource {
  private Long tenantId;
  private Long documentId;
  private Long repairOrderId;
  private Long repairRowVersion;
}
