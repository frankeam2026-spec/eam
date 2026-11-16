package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_item_file的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_item_file")
public class MaintenanceItemFile {
  private Long tenantId;
  private Long standardId;
  private String itemKey;
  private Integer sortNo;
  private String fileId;
  private String nameSnapshot;
  private String contentType;
  private Long sizeBytes;
  private String urlSnapshot;
}
