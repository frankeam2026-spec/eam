package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 已退役字典界面的历史存储模型，仅供既有字段定义和引用读取；不重新开放字典维护。 */
@Data
@TableName("eam_business_option")
public class BusinessOption {
  @TableId(type = IdType.INPUT)
  private String id;

  private Long tenantId;
  private String dictionaryId;
  private String name;
  private Integer sortNo;
  private Integer enabled;
  private Integer rowVersion;
}
