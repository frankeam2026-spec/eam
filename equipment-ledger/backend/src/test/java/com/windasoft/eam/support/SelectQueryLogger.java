package com.windasoft.eam.support;

import com.mysql.cj.log.NullLogger;
import com.mysql.cj.log.ProfilerEvent;
import java.util.Locale;

/**
 * 统计当前线程实际执行的SELECT，不输出SQL或凭据。 Connector/J execute与executeUpdate的Com_select口径不同，写入场景必须按实际查询事件计数。
 */
public final class SelectQueryLogger extends NullLogger {
  private static final ThreadLocal<long[]> COUNTER = new ThreadLocal<>();

  public SelectQueryLogger(String name) {
    super(name);
  }

  public static void start() {
    COUNTER.set(new long[1]);
  }

  public static long stop() {
    var value = COUNTER.get();
    COUNTER.remove();
    if (value == null) throw new IllegalStateException("查询计数尚未开始");
    return value[0];
  }

  public static void clear() {
    COUNTER.remove();
  }

  @Override
  public boolean isInfoEnabled() {
    return true;
  }

  /** Object为驱动SPI要求，业务响应不使用该动态边界。 */
  @Override
  public void logInfo(Object message) {
    var count = COUNTER.get();
    if (count != null
        && message instanceof ProfilerEvent event
        && (event.getEventType() == ProfilerEvent.TYPE_QUERY
            || event.getEventType() == ProfilerEvent.TYPE_EXECUTE)) {
      String sql = event.getMessage().stripLeading().toUpperCase(Locale.ROOT);
      if (sql.startsWith("SELECT ") || sql.startsWith("SELECT\n") || sql.startsWith("WITH "))
        count[0]++;
    }
  }
}
