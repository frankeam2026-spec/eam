package com.windasoft.eam.entity.printing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 打印生成记录：快照与PDF为当时执行产物，金额可见性仍由仓库权限控制。 */
@Data
@TableName("eam_print_job")
public class PrintJob {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private Long warehouseId;
  private String documentType;
  private String documentId;
  private String documentCode;
  private String documentStatus;
  private Long templateId;
  private String templateName;
  private Integer templateVersion;
  private String templateSnapshot;
  private String documentSnapshot;
  private Integer containsMoney;
  private Long actorId;
  private String actorName;
  private String requestKey;
  private String requestHash;
  private String pdfSha256;
  private Integer pageCount;
  private byte[] pdfContent;
  private LocalDateTime createdAt;
}
