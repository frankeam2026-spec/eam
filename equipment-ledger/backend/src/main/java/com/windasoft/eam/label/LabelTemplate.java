package com.windasoft.eam.label;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @TableName("eam_label_template")
public class LabelTemplate {
 @TableId(type=IdType.ASSIGN_ID) private Long id;
 private Long tenantId;
 private String name;
 private BigDecimal widthMm;
 private BigDecimal heightMm;
 private String remark;
 private String elementsJson;
 private String layoutJson;
 @Version private Integer rowVersion;
 @TableLogic private Integer deleted;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
}
