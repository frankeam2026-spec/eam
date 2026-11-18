package com.windasoft.eam.repository.printing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.entity.printing.PrintJob;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PrintJobRepository {
  private final PrintJobMapper mapper;

  public PrintJobRepository(PrintJobMapper mapper) {
    this.mapper = mapper;
  }

  /** 普通列表和锁读不载入PDF大字段及快照，文件入口经同一权限检查后单独读取。 */
  private LambdaQueryWrapper<PrintJob> metadata(long tenant) {
    return new LambdaQueryWrapper<PrintJob>()
        .eq(PrintJob::getTenantId, tenant)
        .select(
            PrintJob::getId,
            PrintJob::getCompanyId,
            PrintJob::getWarehouseId,
            PrintJob::getDocumentId,
            PrintJob::getDocumentCode,
            PrintJob::getDocumentStatus,
            PrintJob::getTemplateId,
            PrintJob::getTemplateName,
            PrintJob::getTemplateVersion,
            PrintJob::getContainsMoney,
            PrintJob::getActorName,
            PrintJob::getCreatedAt,
            PrintJob::getPageCount,
            PrintJob::getPdfSha256);
  }

  public PrintJob find(long tenant, long id, boolean file) {
    var q = metadata(tenant).eq(PrintJob::getId, id);
    if (file)
      q.select(
          PrintJob.class,
          field ->
              List.of("id", "companyId", "warehouseId", "containsMoney", "pdfContent")
                  .contains(field.getProperty()));
    var row = mapper.selectOne(q);
    if (row == null) throw new LocalizedArgumentException("server.printing.message0004");
    return row;
  }

  public PrintJob locked(long tenant, long id) {
    return mapper.selectOne(metadata(tenant).eq(PrintJob::getId, id).last("FOR UPDATE"));
  }

  public List<PrintJob> list(long tenant, String document) {
    return mapper.selectList(
        metadata(tenant)
            .eq(PrintJob::getDocumentId, document)
            .orderByDesc(PrintJob::getCreatedAt, PrintJob::getId)
            .last("LIMIT 100"));
  }

  public PrintJob retry(long tenant, long actor, String key, boolean lock) {
    var q =
        new LambdaQueryWrapper<PrintJob>()
            .select(PrintJob::getId, PrintJob::getRequestHash)
            .eq(PrintJob::getTenantId, tenant)
            .eq(PrintJob::getActorId, actor)
            .eq(PrintJob::getRequestKey, key);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void insertOnce(PrintJob row) {
    mapper.insertOnce(row);
  }
}
