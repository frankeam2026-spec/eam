package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.VersionView;
import com.windasoft.eam.entity.business.BusinessSchemaVersion;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessSchemaVersionRepository {
  private final BusinessSchemaVersionMapper mapper;

  public BusinessSchemaVersionRepository(BusinessSchemaVersionMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(BusinessSchemaVersion version) {
    mapper.insert(version);
  }

  public String configuration(long tenant, String object, int version) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<BusinessSchemaVersion>()
                .eq(BusinessSchemaVersion::getTenantId, tenant)
                .eq(BusinessSchemaVersion::getObjectKey, object)
                .eq(BusinessSchemaVersion::getVersion, version));
    if (row == null) throw new EmptyResultDataAccessException(1);
    return row.getConfiguration();
  }

  public List<VersionView> versions(long tenant, String object) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<BusinessSchemaVersion>()
                .select(
                    BusinessSchemaVersion::getVersion,
                    BusinessSchemaVersion::getActorId,
                    BusinessSchemaVersion::getCreatedAt)
                .eq(BusinessSchemaVersion::getTenantId, tenant)
                .eq(BusinessSchemaVersion::getObjectKey, object)
                .orderByDesc(BusinessSchemaVersion::getVersion))
        .stream()
        .map(row -> new VersionView(row.getVersion(), row.getActorId(), row.getCreatedAt()))
        .toList();
  }
}
