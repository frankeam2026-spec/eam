package com.windasoft.eam.service.system;

import com.windasoft.eam.entity.system.SystemUser;
import java.util.function.Supplier;

/** Internal scheduler identity. Never accepts identity from an HTTP body or header. */
public final class SystemExecution {
  private static final ThreadLocal<SystemUser> USER = new ThreadLocal<>();

  private SystemExecution() {}

  static SystemUser current() {
    return USER.get();
  }

  public static <T> T run(SystemUser user, Supplier<T> action) {
    if (user == null
        || !Integer.valueOf(1).equals(user.getStatus())
        || Integer.valueOf(1).equals(user.getDeleted()))
      throw new IllegalArgumentException("计划创建人已停用");
    if (USER.get() != null) throw new IllegalStateException("不允许嵌套系统身份");
    USER.set(user);
    try {
      return action.get();
    } finally {
      USER.remove();
    }
  }
}
