package com.windasoft.eam.domain.business;

import com.windasoft.eam.common.LocalizedArgumentException;

/** 引用来源使用既有协议编码；类型选择仅决定受控查询，不接受客户端表名或列名。 */
public record BusinessReferenceSource(Kind kind, String key) {
  public enum Kind {
    FIELD,
    DICTIONARY,
    BASE,
    USER,
    DEPARTMENT
  }

  public static BusinessReferenceSource parse(String source) {
    if (source != null) {
      if (source.startsWith("field:"))
        return new BusinessReferenceSource(Kind.FIELD, source.substring(6));
      if (source.startsWith("dictionary:"))
        return new BusinessReferenceSource(Kind.DICTIONARY, source.substring(11));
      if (source.startsWith("base:"))
        return new BusinessReferenceSource(Kind.BASE, source.substring(5));
      if (source.equals("user")) return new BusinessReferenceSource(Kind.USER, "");
      if (source.equals("department")) return new BusinessReferenceSource(Kind.DEPARTMENT, "");
    }
    throw new LocalizedArgumentException("server.business.message0002");
  }
}
