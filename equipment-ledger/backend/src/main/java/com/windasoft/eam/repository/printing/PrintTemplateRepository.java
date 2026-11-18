package com.windasoft.eam.repository.printing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.entity.printing.PrintTemplate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PrintTemplateRepository {
  private final PrintTemplateMapper mapper;

  public PrintTemplateRepository(PrintTemplateMapper mapper) {
    this.mapper = mapper;
  }

  public List<PrintTemplate> list(long tenant) {
    return mapper.listed(tenant);
  }

  public PrintTemplate find(long tenant, long id) {
    var t =
        mapper.selectOne(
            new LambdaQueryWrapper<PrintTemplate>()
                .eq(PrintTemplate::getTenantId, tenant)
                .eq(PrintTemplate::getId, id)
                .eq(PrintTemplate::getDeleted, 0));
    if (t == null) throw new LocalizedArgumentException("server.printing.message0001");
    return t;
  }

  /** 与模板保存共用外层事务，数据库生成列唯一键继续约束同范围只有一个默认模板。 */
  public void clearDefaults(long tenant, Long company, long except) {
    var q =
        new LambdaUpdateWrapper<PrintTemplate>()
            .eq(PrintTemplate::getTenantId, tenant)
            .eq(PrintTemplate::getDeleted, 0)
            .eq(PrintTemplate::getIsDefault, 1)
            .ne(PrintTemplate::getId, except);
    if (company == null) q.isNull(PrintTemplate::getCompanyId);
    else q.eq(PrintTemplate::getCompanyId, company);
    mapper.update(null, q.set(PrintTemplate::getIsDefault, 0).setSql("row_version=row_version+1"));
  }

  public void insert(PrintTemplate row) {
    mapper.insert(row);
  }

  public int update(PrintTemplate row, int version) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<PrintTemplate>()
            .eq(PrintTemplate::getTenantId, row.getTenantId())
            .eq(PrintTemplate::getId, row.getId())
            .eq(PrintTemplate::getDeleted, 0)
            .eq(PrintTemplate::getRowVersion, version)
            .set(PrintTemplate::getName, row.getName())
            .set(PrintTemplate::getCompanyId, row.getCompanyId())
            .set(PrintTemplate::getEnabled, row.getEnabled())
            .set(PrintTemplate::getIsDefault, row.getIsDefault())
            .set(PrintTemplate::getConfigJson, row.getConfigJson())
            .setSql("row_version=row_version+1"));
  }

  public int delete(long tenant, long id, int version) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<PrintTemplate>()
            .eq(PrintTemplate::getTenantId, tenant)
            .eq(PrintTemplate::getId, id)
            .eq(PrintTemplate::getRowVersion, version)
            .eq(PrintTemplate::getDeleted, 0)
            .set(PrintTemplate::getDeleted, 1)
            .set(PrintTemplate::getIsDefault, 0)
            .setSql("row_version=row_version+1"));
  }
}
