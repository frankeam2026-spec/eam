package com.windasoft.eam.repository.equipment;

import static com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.*;

import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.common.AttachmentRelationsRepository;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentMediaRepository {
  private final AttachmentRelationsRepository files;

  public EquipmentMediaRepository(AttachmentRelationsRepository files) {
    this.files = files;
  }

  public Equipment hydrate(Equipment row) {
    if (row != null) hydrate(List.of(row));
    return row;
  }

  public <T extends List<Equipment>> T hydrate(T rows) {
    for (var group :
        rows.stream()
            .filter(e -> e.getId() != null && e.getTenantId() != null)
            .collect(java.util.stream.Collectors.groupingBy(Equipment::getTenantId))
            .entrySet()) {
      var ids = group.getValue().stream().map(Equipment::getId).toList();
      var images = files.load(EQUIPMENT_IMAGE, group.getKey(), ids);
      var docs = files.load(EQUIPMENT_DOCUMENT, group.getKey(), ids);
      group
          .getValue()
          .forEach(
              e -> {
                e.setImagesJson(images.get(e.getId()));
                e.setDocumentsJson(docs.get(e.getId()));
              });
    }
    return rows;
  }

  public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Equipment> hydratePage(
      com.baomidou.mybatisplus.extension.plugins.pagination.Page<Equipment> page) {
    hydrate(page.getRecords());
    return page;
  }

  public void save(Equipment row) {
    files.save(EQUIPMENT_IMAGE, row.getTenantId(), row.getId(), row.getImagesJson());
    files.save(EQUIPMENT_DOCUMENT, row.getTenantId(), row.getId(), row.getDocumentsJson());
  }
}
