package com.windasoft.eam.dto.maintenance;

/** 保留旧待办协议：todo/done/isRead为数字，messageArgs仍为历史JSON文本。 */
public record MaintenanceInboxView(
    String id,
    String orderId,
    String module,
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
    String time) {
  public MaintenanceInboxView withTitle(String value) {
    return new MaintenanceInboxView(
        id,
        orderId,
        module,
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
        time);
  }
}
