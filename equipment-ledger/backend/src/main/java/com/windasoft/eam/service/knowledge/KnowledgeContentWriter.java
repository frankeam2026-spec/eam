package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.knowledge.KnowledgeContentRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal worker boundary; parsing happens outside the transaction, content commit is atomic. */
@Service
public class KnowledgeContentWriter {
  private final KnowledgeContentRepository repository;

  public KnowledgeContentWriter(KnowledgeContentRepository repository) { this.repository = repository; }

  @Transactional
  public void commit(long tenant, long version, List<KnowledgePdfParser.Page> parsed) {
    commitAttempt(tenant,version,parsed,-1);
  }
  @Transactional
  public void commitAttempt(long tenant,long version,List<KnowledgePdfParser.Page> parsed,int attempt){
    if (tenant <= 0 || version == 0 || parsed == null || parsed.isEmpty())
      throw new IllegalArgumentException("文档解析结果无效");
    for (int i = 0; i < parsed.size(); i++) {
      var p = parsed.get(i);
      if (p.pageNo() != i + 1 || p.text() == null || p.chunks() == null
          || p.needsOcr() && !p.chunks().isEmpty()
          || p.chunks().stream().anyMatch(c -> c == null || c.isBlank()))
        throw new IllegalArgumentException("文档页码或切片无效");
    }
    if (!(attempt<0?repository.lockParsingVersion(tenant, version):repository.lockParsingAttempt(tenant,version,attempt)))
      throw new IllegalStateException("版本不存在、已处理或已发布，请重新读取解析任务状态");
    var pages = new ArrayList<KnowledgeContentRepository.Page>();
    for (var p : parsed) {
      var chunks = new ArrayList<KnowledgeContentRepository.Chunk>();
      for (int i = 0; i < p.chunks().size(); i++)
        chunks.add(new KnowledgeContentRepository.Chunk(IdWorker.getId(), i + 1, p.chunks().get(i)));
      pages.add(new KnowledgeContentRepository.Page(IdWorker.getId(), p.pageNo(), p.text(), p.needsOcr(), List.copyOf(chunks)));
    }
    repository.insertPages(tenant, version, pages);
  }
}
