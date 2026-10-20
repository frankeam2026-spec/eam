package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InspectionValidationService {
  private final InspectionRepository repository;

  public InspectionValidationService(InspectionRepository repository) {
    this.repository = repository;
  }

  void users(long tenant, Object value, boolean required) {
    var selected = ids(value);
    require(
        !required || !selected.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("ui.equipment.text0336"));
    for (var id : selected)
      require(
          repository.countActiveUser(tenant, id) > 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0074"));
  }

  void attachments(long tenant, Object value, boolean images) {
    for (var a : rows(value)) {
      String url = text(a, "url");
      require(
          url.matches("/api/v1/inspection/files/[0-9a-f-]{36}"),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0075"));
      String id = url.substring(url.lastIndexOf('/') + 1);
      require(repository.countAttachment(tenant, id, images) > 0, images ? "请上传图片" : "附件不存在");
    }
  }
}
