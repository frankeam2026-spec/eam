package com.windasoft.eam.config;

import java.sql.*;
import org.flywaydb.core.api.callback.*;
import org.springframework.stereotype.Component;

/** V25 reserved MAX(id)+offset IDs without consuming UUID_SHORT. Preserve old checksums. */
@Component
public class PermissionMigrationIds extends BaseCallback {
  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_EACH_MIGRATE
        && context.getMigrationInfo() != null
        && context.getMigrationInfo().getVersion() != null
        && context.getMigrationInfo().getVersion().toString().equals("61");
  }

  @Override
  public void handle(Event event, Context context) {
    try {
      var connection = context.getConnection();
      long candidate, needed;
      try (var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  "SELECT UUID_SHORT(),(SELECT COUNT(*)+1 FROM eam_sys_role WHERE code='SYS_ADMIN'"
                      + " AND deleted=0)")) {
        result.next();
        candidate = result.getLong(1);
        needed = result.getLong(2);
      }
      // Consume only a conflicting nearby range; do not compare to unrelated Snowflake IDs.
      while (true) {
        long collision;
        try (var statement =
            connection.prepareStatement(
                "SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission WHERE id>? AND"
                    + " id<=?")) {
          statement.setLong(1, candidate);
          statement.setLong(2, candidate + needed);
          try (var result = statement.executeQuery()) {
            result.next();
            collision = result.getLong(1);
          }
        }
        if (collision == 0) break;
        while (candidate < collision)
          try (var statement = connection.createStatement();
              var result = statement.executeQuery("SELECT UUID_SHORT()")) {
            result.next();
            candidate = result.getLong(1);
          }
      }
    } catch (SQLException error) {
      throw new IllegalStateException("无法准备历史权限迁移编号", error);
    }
  }
}
