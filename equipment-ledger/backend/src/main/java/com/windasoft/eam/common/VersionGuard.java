package com.windasoft.eam.common;

import java.util.Objects;
import org.springframework.http.HttpStatus;

public final class VersionGuard {
  private VersionGuard() {}

  public static void check(Integer currentVersion, int expectedVersion) {
    if (expectedVersion < 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0017");
    if (!Objects.equals(currentVersion, expectedVersion)) conflict();
  }

  public static void checkPayload(Number currentVersion, java.util.Map<String, Object> body) {
    Object value = body.get("rowVersion");
    if (!(value instanceof Integer || value instanceof Long))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0018");
    long expected = ((Number) value).longValue();
    if (expected < 0 || expected > Integer.MAX_VALUE)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0017");
    check(currentVersion.intValue(), (int) expected);
  }

  public static void updated(int affectedRows) {
    if (affectedRows != 1) conflict();
  }

  private static void conflict() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.CONFLICT, "server.common.message0019");
  }
}
