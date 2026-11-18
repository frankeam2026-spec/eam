package com.windasoft.eam.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mysql.cj.log.ProfilerEvent;
import org.junit.jupiter.api.Test;

class SelectQueryLoggerTest {
  @Test
  void countsExecutedSelectsAndExcludesWritesPrepareAndFetch() {
    var logger = new SelectQueryLogger("test");
    var event = mock(ProfilerEvent.class);
    SelectQueryLogger.start();
    try {
      when(event.getEventType()).thenReturn(ProfilerEvent.TYPE_QUERY);
      when(event.getMessage()).thenReturn(" SELECT 1");
      logger.logInfo(event);
      when(event.getMessage()).thenReturn("INSERT INTO sample VALUES(1)");
      logger.logInfo(event);
      when(event.getMessage()).thenReturn("SELECT 2");
      when(event.getEventType()).thenReturn(ProfilerEvent.TYPE_FETCH);
      logger.logInfo(event);
      when(event.getEventType()).thenReturn(ProfilerEvent.TYPE_PREPARE);
      logger.logInfo(event);
      assertEquals(1, SelectQueryLogger.stop());
    } finally {
      SelectQueryLogger.clear();
    }
  }
}
