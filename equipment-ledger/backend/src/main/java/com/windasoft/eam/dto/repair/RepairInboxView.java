package com.windasoft.eam.dto.repair;

import lombok.Data;

/** 工作台消息投影：保留旧字段及数值型已读/待办标志，消息参数仅用于国际化渲染。 */
@Data
public class RepairInboxView {
  private String id;
  private String orderId;
  private String kind;
  private String title;
  private String messageKey;
  private String messageArgs;
  private Integer todo;
  private Integer done;
  private Integer isRead;
  private String time;
  private String orderNo;
  private String equipmentName;
  private String status;
}
