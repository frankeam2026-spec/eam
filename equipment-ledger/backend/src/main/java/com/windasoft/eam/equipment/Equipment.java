package com.windasoft.eam.equipment;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_equipment")
public class Equipment {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long companyOrgId;
  private Long departmentOrgId;
  private Long ownerUserId;
  private String equipmentCode;
  private String assetNo;
  private String tagCode;
  private String name;
  private String category;
  private String model;
  private String serialNo;
  private String company;
  private String department;
  private String ownerName;
  private String brand;
  private String equipmentStatus;
  private String useStatus;
  private String source;
  private String unitName;
  private String supplier;
  private LocalDate purchaseDate;
  private LocalDate acquiredAt;
  private BigDecimal acquiredAmount;
  private LocalDate warrantyUntil;
  private LocalDate inServiceDate;
  private LocalDate expectedRetirementDate;
  private String equipmentLevel;
  private LocalDate calibrationDate;
  private LocalDate calibrationValidUntil;
  private String inspectorName;
  private String functionLocation;
  private String specialEquipment;
  private String metrologyEquipment;
  private String depreciationEnabled;
  private String technicalParams;
  private String imageName;
  private String imageFileId;
  private String documentsJson;
  private String childrenJson;
  private String relatedSparesJson;
  private String description;
  @Version private Integer rowVersion;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  @TableLogic private Integer deleted;
}
