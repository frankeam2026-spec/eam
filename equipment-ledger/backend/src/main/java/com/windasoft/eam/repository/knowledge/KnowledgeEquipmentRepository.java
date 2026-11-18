package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.knowledge.KnowledgeEquipment;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeEquipmentRepository {
  private final KnowledgeEquipmentMapper mapper;

  public KnowledgeEquipmentRepository(KnowledgeEquipmentMapper mapper) {
    this.mapper = mapper;
  }

  public List<KnowledgeEquipment> list(long tenant, long document) {
    return mapper.selectList(
        new LambdaQueryWrapper<KnowledgeEquipment>()
            .eq(KnowledgeEquipment::getTenantId, tenant)
            .eq(KnowledgeEquipment::getDocumentId, document));
  }

  public long count(long tenant, long document) {
    return mapper.selectCount(
        new LambdaQueryWrapper<KnowledgeEquipment>()
            .eq(KnowledgeEquipment::getTenantId, tenant)
            .eq(KnowledgeEquipment::getDocumentId, document));
  }

  public void replace(long tenant, long document, List<Long> equipment) {
    mapper.delete(
        new LambdaQueryWrapper<KnowledgeEquipment>()
            .eq(KnowledgeEquipment::getTenantId, tenant)
            .eq(KnowledgeEquipment::getDocumentId, document));
    if (!equipment.isEmpty())
      mapper.insert(
          equipment.stream()
              .map(
                  id -> {
                    var row = new KnowledgeEquipment();
                    row.setTenantId(tenant);
                    row.setDocumentId(document);
                    row.setEquipmentId(id);
                    return row;
                  })
              .toList(),
          200);
  }
}
