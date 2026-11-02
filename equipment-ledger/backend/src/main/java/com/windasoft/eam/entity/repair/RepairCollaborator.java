package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 协同人员按租户、工单、用户复合键定位，不伪造单列主键。 */
@Data
@TableName("eam_repair_collaborator")
public class RepairCollaborator {
  private Long tenantId;
  private Long orderId;
  private Long userId;

  @com.baomidou.mybatisplus.annotation.TableField(
      insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
  private String nameSnapshot;

  private Integer sortNo;
}
