package com.windasoft.eam.dto.common;

import java.util.List;

public record PageResult<T>(List<T> data, Page page) {
  public record Page(int number, int size, long total) {}

  public PageResult(List<T> rows, int number, int size, long total) {
    this(rows, new Page(number, size, total));
  }
}
