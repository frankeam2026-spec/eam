package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.business.BusinessChoiceQuery;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.OrgUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 组织表的数据访问，业务事务和多组织加锁顺序由调用Service编排。 */
/** 组织单元仓储：保存组织层级和状态，层级合法性由组织业务服务决定。 */
@Repository
public class OrgUnitRepository {
  private final OrgUnitMapper mapper;
  private final JdbcTemplate db;

  public OrgUnitRepository(OrgUnitMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public boolean lockExisting(long tenant, long id) {
    // 沿用原条件：同租户且未删除，不追加启用状态或组织类型限制。
    return mapper.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .select(OrgUnit::getId)
                .eq(OrgUnit::getTenantId, tenant)
                .eq(OrgUnit::getId, id)
                .last("FOR UPDATE"))
        != null;
  }

  public OrgUnit activeDepartment(long tenant, String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "部门")
            .eq(OrgUnit::getStatus, 1));
  }

  public OrgUnit organization(Long id) {
    return mapper.selectById(id);
  }

  public List<OrgUnit> organizations(long tenant, boolean activeOnly) {
    return mapper.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(activeOnly, OrgUnit::getStatus, 1));
  }

  public void lockOrganizations(long tenant) {
    db.queryForList(
        "SELECT id FROM eam_org_unit WHERE tenant_id=? AND deleted=0 ORDER BY id FOR UPDATE",
        tenant);
  }

  public List<OrgUnit> organizations(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .orderByAsc(OrgUnit::getId));
  }

  public OrgUnit organization(long tenant, Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, tenant).eq(OrgUnit::getId, id));
  }

  public int insertOrganization(OrgUnit x) {
    return mapper.insert(x);
  }

  public int updateOrganization(OrgUnit x) {
    return mapper.updateById(x);
  }

  public int deleteOrganization(Long id) {
    return mapper.deleteById(id);
  }

  public long childOrganizationCount(long tenant, Long id) {
    return mapper.selectCount(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getParentId, id));
  }

  public long rootCount(long tenant, Long current) {
    var q =
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .isNull(OrgUnit::getParentId);
    if (current != null) q.ne(OrgUnit::getId, current);
    return mapper.selectCount(q);
  }

  public long orgCodeCount(long tenant, String code, Long current) {
    var q =
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getCode, code.trim());
    if (current != null) q.ne(OrgUnit::getId, current);
    return mapper.selectCount(q);
  }

  public long organizationCount(long tenant, String type, List<Long> ids) {
    return mapper.selectCount(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getOrgType, type)
            .in(OrgUnit::getId, ids));
  }

  public List<OrgUnit> companies(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .in(OrgUnit::getOrgType, "集团", "公司")
            .orderByAsc(OrgUnit::getId));
  }

  /** 打印公司候选保留原启用、集团/公司、未删除和ID排序语义。 */
  public List<OrgUnit> activeCompanies(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getStatus, 1)
            .in(OrgUnit::getOrgType, "集团", "公司")
            .orderByAsc(OrgUnit::getId));
  }

  /** 知识资料只允许启用的公司，沿用原授权集合和名称/ID排序，不扩为集团。 */
  public List<OrgUnit> knowledgeCompanies(long tenant, PermissionScope scope) {
    if (!scope.all() && scope.companyIds().isEmpty()) return List.of();
    return mapper.selectList(
        new QueryWrapper<OrgUnit>()
            .select("id", "name")
            .eq("tenant_id", tenant)
            .eq("org_type", "公司")
            .eq("status", 1)
            .in(!scope.all(), "id", scope.companyIds())
            .orderByAsc("name", "CAST(id AS CHAR)"));
  }

  public OrgUnit company(long tenant, Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "公司"));
  }

  /** 授权集合由Service计算，数据库只在该集合内完成匹配、计数和分页；空集合不能省略授权条件。 */
  public Page<OrgUnit> businessChoices(
      long tenant, BusinessChoiceQuery query, Collection<Long> allowed) {
    var where =
        new QueryWrapper<OrgUnit>()
            .select("id", "name", "status")
            .eq("tenant_id", tenant)
            .eq("status", 1)
            .eq("org_type", "部门")
            .apply("LOWER(name) LIKE {0} ESCAPE '!'", query.likePattern())
            .orderByAsc("name", "CAST(id AS CHAR)");
    if (allowed.isEmpty()) where.apply("0=1");
    else where.in("id", allowed);
    return mapper.selectPage(new Page<>(query.page(), query.size()), where);
  }

  public OrgUnit referenceForShare(long tenant, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .select(OrgUnit::getId, OrgUnit::getName, OrgUnit::getParentId, OrgUnit::getStatus)
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "部门")
            .last("FOR SHARE"));
  }
}
