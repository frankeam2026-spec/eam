package com.windasoft.eam.repository.repair;

import com.windasoft.eam.entity.repair.RepairUploadRecord;
import org.springframework.stereotype.Repository;

/** 上传登记主仓储，不判断工单业务权限，也不维护客户端声明的关联。 */
@Repository
public class RepairUploadRepository {
  private final RepairUploadMapper mapper;

  public RepairUploadRepository(RepairUploadMapper mapper) {
    this.mapper = mapper;
  }

  /** 按全局文件键读取原始归属，供服务识别并拒绝其他租户；不得把跨租户记录当作无登记历史文件。 */
  public RepairUploadRecord ownership(String id) {
    return mapper.selectById(id);
  }

  public void register(RepairUploadRecord row) {
    mapper.insert(row);
  }
}
