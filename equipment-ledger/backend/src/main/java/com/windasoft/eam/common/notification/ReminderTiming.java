package com.windasoft.eam.common.notification;

import java.time.LocalDateTime;
import java.util.*;

/** Normal workflow deadlines only; no suspension accounting or workflow rounds. */
public final class ReminderTiming {
  private ReminderTiming() {}
  public record Stage(String scene,LocalDateTime anchor) {
    public LocalDateTime trigger(long seconds) { return scene.equals("BEFORE_DUE")?anchor.minusSeconds(seconds):anchor.plusSeconds(seconds); }
  }
  public static Map<String,Stage> repair(LocalDateTime reported,LocalDateTime dispatched,LocalDateTime claimed,LocalDateTime submitted,String status,boolean specialFlow) {
    var stages=new LinkedHashMap<String,Stage>();
    if(specialFlow)return stages;
    if(status.equals("待派工") && reported!=null)stages.put("WAIT_DISPATCH",new Stage("WAIT_DISPATCH",reported));
    if(Set.of("待派工","待接单").contains(status) && reported!=null && claimed==null)stages.put("REPORT_UNCLAIMED",new Stage("REPORT_UNCLAIMED",reported));
    if(status.equals("待接单") && dispatched!=null)stages.put("WAIT_CLAIM",new Stage("WAIT_CLAIM",dispatched));
    if(status.equals("处理中") && claimed!=null)stages.put("PROCESS",new Stage("PROCESS",claimed));
    if(status.equals("待验收") && submitted!=null)stages.put("WAIT_REVIEW",new Stage("WAIT_REVIEW",submitted));
    return stages;
  }
  public static long seconds(int value,String unit) {
    return Math.multiplyExact((long)value,switch(unit){case "MINUTE"->60;case "HOUR"->3600;case "DAY"->86400;default->throw new IllegalArgumentException("Invalid duration unit");});
  }
}
