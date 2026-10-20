package com.windasoft.eam.repository.i18n;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LanguageRepository {
  private final JdbcTemplate db;

  public LanguageRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> list(long tenant) {
    return db.queryForList(
        "SELECT locale,native_name nativeName,builtin,enabled,row_version rowVersion,updated_at"
            + " updatedAt FROM eam_language WHERE tenant_id=? ORDER BY builtin DESC,locale",
        tenant);
  }

  public Map<String, Object> find(long tenant, String locale) {
    var rows =
        db.queryForList(
            "SELECT * FROM eam_language WHERE tenant_id=? AND locale=?", tenant, locale);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Map<String, String> messages(long tenant, String locale) {
    var result = new LinkedHashMap<String, String>();
    db.query(
        "SELECT message_key,message_text FROM eam_language_message WHERE tenant_id=? AND locale=?",
        rs -> {
          result.put(rs.getString(1), rs.getString(2));
        },
        tenant,
        locale);
    return result;
  }

  public String preference(long tenant, long user) {
    var a =
        db.queryForList(
            "SELECT locale FROM eam_user_preference WHERE tenant_id=? AND user_id=?",
            String.class,
            tenant,
            user);
    return a.isEmpty() ? "zh-CN" : a.getFirst();
  }

  public void preference(long tenant, long user, String locale) {
    db.update(
        "INSERT INTO eam_user_preference(tenant_id,user_id,theme,locale) VALUES(?,?,'blue',?) ON"
            + " DUPLICATE KEY UPDATE locale=VALUES(locale)",
        tenant,
        user,
        locale);
  }

  public boolean update(
      long tenant, String locale, int version, String name, boolean enabled, long actor) {
    return db.update(
            "UPDATE eam_language SET native_name=?,enabled=?,row_version=row_version+1,updated_by=?"
                + " WHERE tenant_id=? AND locale=? AND row_version=?",
            name,
            enabled,
            actor,
            tenant,
            locale,
            version)
        == 1;
  }

  public void insert(long tenant, String locale, String name, long actor, boolean builtin) {
    db.update(
        "INSERT INTO"
            + " eam_language(tenant_id,locale,native_name,builtin,enabled,row_version,updated_by)"
            + " VALUES(?,?,?,?,?,1,?)",
        tenant,
        locale,
        name,
        builtin,
        builtin,
        actor);
  }

  public void replace(long tenant, String locale, Map<String, String> messages) {
    db.update("DELETE FROM eam_language_message WHERE tenant_id=? AND locale=?", tenant, locale);
    db.batchUpdate(
        "INSERT INTO eam_language_message(tenant_id,locale,message_key,message_text,module_key)"
            + " VALUES(?,?,?,?,?)",
        messages.entrySet(),
        500,
        (ps, e) -> {
          ps.setLong(1, tenant);
          ps.setString(2, locale);
          ps.setString(3, e.getKey());
          ps.setString(4, e.getValue());
          ps.setString(
              5, String.join(".", Arrays.stream(e.getKey().split("\\.")).limit(2).toList()));
        });
  }

  public void event(long tenant, String locale, String action, long actor, int version, int count) {
    db.update(
        "INSERT INTO"
            + " eam_language_event(tenant_id,locale,action_code,actor_id,row_version,message_count)"
            + " VALUES(?,?,?,?,?,?)",
        tenant,
        locale,
        action,
        actor,
        version,
        count);
  }
}
