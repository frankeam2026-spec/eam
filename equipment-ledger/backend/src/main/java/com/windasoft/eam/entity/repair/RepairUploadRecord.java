package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 维修上传文件的原始归属；全局文件ID对应唯一租户，不根据客户端后补关联重新认定归属。 */
@Data
@TableName("eam_repair_upload")
public class RepairUploadRecord {
  @TableId(type = IdType.INPUT)
  private String id;

  private Long tenantId;
  private Long uploadedBy;

  @TableField(insertStrategy = FieldStrategy.NEVER)
  private LocalDateTime createdAt;
}
