package com.windasoft.eam.service.repair;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.repository.repair.RepairUploadRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 保存关系之前校验文件原归属，防止通过新建可见工单洗白其他租户或无权工单的附件。 */
@Service
public class RepairAttachmentAccessService {
  private final RepairUploadRepository uploads;
  private final RepairOrderRepository orders;
  private final RepairAccessService access;
  private final AuthContext auth;

  public RepairAttachmentAccessService(
      RepairUploadRepository uploads,
      RepairOrderRepository orders,
      RepairAccessService access,
      AuthContext auth) {
    this.uploads = uploads;
    this.orders = orders;
    this.access = access;
    this.auth = auth;
  }

  public void requireAttachable(String id) {
    var original = uploads.ownership(id);
    if (original != null) {
      if (!Objects.equals(original.getTenantId(), auth.tenantId())) {
        throw invalid();
      }
      if (Objects.equals(original.getUploadedBy(), auth.userId())) {
        return;
      }
    }
    for (var row : orders.attachmentOrders(auth.tenantId(), id)) {
      try {
        access.requireView(row);
        return;
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) {
          throw denied;
        }
      }
    }
    throw invalid();
  }

  private LocalizedArgumentException invalid() {
    return new LocalizedArgumentException("server.repair.message0011");
  }
}
