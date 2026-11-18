package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 品牌图片归租户及上传人所有；元信息校验不加载图片内容。 */
@Data
@TableName("eam_branding_asset")
public class BrandingAsset {
  @TableId(type = IdType.INPUT)
  private String id;

  private Long tenantId;
  private String kind;
  private String contentType;
  private byte[] imageData;
  private Long createdBy;
  private LocalDateTime createdAt;
}
