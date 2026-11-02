package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_repair_candidate 的持久化对象；业务状态与事务由服务层维护。 */
@Data
@TableName("eam_repair_candidate")
public class RepairCandidate {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long orderId;
  private Long userId;
  private String status;
  private LocalDateTime createdAt;
}
