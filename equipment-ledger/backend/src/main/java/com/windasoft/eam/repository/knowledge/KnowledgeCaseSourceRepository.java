package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.entity.knowledge.KnowledgeCaseSource;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeCaseSourceRepository {
  private final KnowledgeCaseSourceMapper mapper;

  public KnowledgeCaseSourceRepository(KnowledgeCaseSourceMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(KnowledgeCaseSource row) {
    mapper.insert(row);
  }
}
