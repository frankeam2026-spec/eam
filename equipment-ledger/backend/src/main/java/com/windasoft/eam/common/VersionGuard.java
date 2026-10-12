package com.windasoft.eam.common;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class VersionGuard {
  private VersionGuard() {}

  public static void check(Integer currentVersion, int expectedVersion) {
    if (expectedVersion < 0) throw new IllegalArgumentException("数据版本无效");
    if (!Objects.equals(currentVersion, expectedVersion)) conflict();
  }

  public static void checkPayload(Number currentVersion, java.util.Map<String, Object> body) {
    Object value = body.get("rowVersion");
    if (!(value instanceof Integer || value instanceof Long))
      throw new IllegalArgumentException("请提交有效的数据版本，请刷新后重试");
    long expected = ((Number) value).longValue();
    if (expected < 0 || expected > Integer.MAX_VALUE) throw new IllegalArgumentException("数据版本无效");
    check(currentVersion.intValue(), (int) expected);
  }

  public static void updated(int affectedRows) {
    if (affectedRows != 1) conflict();
  }

  private static void conflict() {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "数据已被他人修改，请刷新后重试");
  }
}
