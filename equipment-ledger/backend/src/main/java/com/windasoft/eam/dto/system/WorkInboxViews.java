package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public final class WorkInboxViews {
  private WorkInboxViews() {}

  public record Counts(long pending, long completed, long unread) {}

  public record KindCount(String module, String kind, long count, long completed) {}

  public record Kind(String module, String kind) {}

  public record Summary(Counts counts, List<KindCount> kinds) {}

  public record Row(
      String id,
      String module,
      String orderId,
      String orderNo,
      String equipmentName,
      String status,
      String kind,
      String title,
      String messageKey,
      String messageArgs,
      int todo,
      int done,
      int isRead,
      String planName,
      String shiftName,
      String time) {
    public Row translated(String value) {
      return new Row(
          id,
          module,
          orderId,
          orderNo,
          equipmentName,
          status,
          kind,
          value,
          messageKey,
          messageArgs,
          todo,
          done,
          isRead,
          planName,
          shiftName,
          time);
    }
  }

  public record Page(List<Row> data, long total) {}

  public record Card(@JsonUnwrapped Row row, String code, String name, String url) {}

  public record Block(List<Card> data, long total, String url) {}

  public record Read(String module, String id) {}
}
