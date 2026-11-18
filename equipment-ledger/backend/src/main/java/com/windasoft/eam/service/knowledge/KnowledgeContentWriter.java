package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.entity.knowledge.KnowledgeChunk;
import com.windasoft.eam.entity.knowledge.KnowledgePage;
import com.windasoft.eam.repository.knowledge.KnowledgeChunkRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeContentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgePageRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 解析在事务外完成；页面、切片及解析状态在原单一事务提交，已发布或已有内容不可覆盖。 */
@Service
public class KnowledgeContentWriter {
  private final KnowledgeContentRepository locks;
  private final KnowledgePageRepository pages;
  private final KnowledgeChunkRepository chunks;
  private final KnowledgeVersionRepository versions;

  public KnowledgeContentWriter(
      KnowledgeContentRepository locks,
      KnowledgePageRepository pages,
      KnowledgeChunkRepository chunks,
      KnowledgeVersionRepository versions) {
    this.locks = locks;
    this.pages = pages;
    this.chunks = chunks;
    this.versions = versions;
  }

  @Transactional
  public void commit(long tenant, long version, List<KnowledgePdfParser.Page> parsed) {
    write(tenant, version, parsed, null);
  }

  /** 保存前校验整包与租约，仍先锁版本/文档再写页和切片，任一批次失败应整体回滚。 */
  @Transactional
  public void commitAttempt(
      long tenant, long version, List<KnowledgePdfParser.Page> parsed, String token) {
    write(tenant, version, parsed, Objects.requireNonNull(token));
  }

  private void write(
      long tenant, long version, List<KnowledgePdfParser.Page> parsed, String token) {
    if (tenant <= 0 || version == 0 || parsed == null || parsed.isEmpty())
      throw new IllegalArgumentException("文档解析结果无效");
    for (int i = 0; i < parsed.size(); i++) {
      var p = parsed.get(i);
      if (p.pageNo() != i + 1
          || p.text() == null
          || p.chunks() == null
          || p.needsOcr() && !p.chunks().isEmpty()
          || p.chunks().stream().anyMatch(c -> c == null || c.isBlank()))
        throw new IllegalArgumentException("文档页码或切片无效");
    }
    if (!(token == null
        ? locks.lockParsingVersion(tenant, version)
        : locks.lockParsingAttempt(tenant, version, token)))
      throw new IllegalStateException("版本不存在、已处理或已发布，请重新读取解析任务状态");
    var pageRows = new ArrayList<KnowledgePage>();
    var chunkRows = new ArrayList<KnowledgeChunk>();
    for (var p : parsed) {
      long pageId = IdWorker.getId();
      var page = new KnowledgePage();
      page.setId(pageId);
      page.setTenantId(tenant);
      page.setVersionId(version);
      page.setPageNo(p.pageNo());
      page.setTextContent(p.text());
      page.setNeedsOcr(p.needsOcr());
      pageRows.add(page);
      for (int i = 0; i < p.chunks().size(); i++) {
        var chunk = new KnowledgeChunk();
        chunk.setId(IdWorker.getId());
        chunk.setTenantId(tenant);
        chunk.setPageId(pageId);
        chunk.setSequenceNo(i + 1);
        chunk.setTextContent(p.chunks().get(i));
        chunk.setContentHash(hash(p.chunks().get(i)));
        chunkRows.add(chunk);
      }
    }
    pages.insert(pageRows);
    chunks.insert(chunkRows);
    versions.finishParsed(
        tenant, version, parsed.stream().anyMatch(KnowledgePdfParser.Page::needsOcr));
  }

  private String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
