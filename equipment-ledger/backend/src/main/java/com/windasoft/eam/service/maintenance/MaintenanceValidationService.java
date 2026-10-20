package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceValidationService {
  private final MaintenanceRepository repository;
  private final AuthContext auth;

  public MaintenanceValidationService(MaintenanceRepository repository, AuthContext auth) {
    this.repository = repository;
    this.auth = auth;
  }

  void validUsers(Object value, boolean required) {
    var ids = ids(value);
    require(
        !required || !ids.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("ui.equipment.text0336"));
    for (var id : ids)
      require(
          repository.countActiveUser(auth.tenantId(), id) > 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0013"));
  }

  void validAttachments(Object value, boolean imagesOnly) {
    for (var a : rows(value)) {
      var url = text(a, "url");
      require(
          url.matches("/api/v1/maintenance/files/[0-9a-f-]{36}"),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0075"));
      require(
          repository.countAttachment(auth.tenantId(), url.substring(url.lastIndexOf('/') + 1)) > 0,
          com.windasoft.eam.common.MessageContext.text("server.repair.message0011"));
      if (imagesOnly)
        require(
            repository.countImageAttachment(
                    auth.tenantId(), url.substring(url.lastIndexOf('/') + 1))
                > 0,
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0014"));
    }
  }
}
