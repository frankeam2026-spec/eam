package com.windasoft.eam.repository.experience;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepository {
  private final JdbcTemplate db;

  public HomeRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> loadPagePreference(long tenantId, long userId, Object key) {
    return db.queryForList(
        "SELECT preferences FROM eam_experience_page_preference WHERE tenant_id=? AND user_id=?"
            + " AND page_key=?",
        tenantId,
        userId,
        key);
  }

  public int savePagePreference(long tenantId, long userId, Object key, Object encoded) {
    return db.update(
        "INSERT INTO eam_experience_page_preference(tenant_id,user_id,page_key,preferences)"
            + " VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE preferences=VALUES(preferences)",
        tenantId,
        userId,
        key,
        encoded);
  }

  public List<Map<String, Object>> findPresetRole(long tenantId, Object value) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id FROM eam_sys_role WHERE tenant_id=? AND status=1 AND"
            + " code=?",
        tenantId,
        value);
  }

  public int insertPreset(
      Object tid,
      long tenantId,
      Object value,
      String payload,
      String payload5,
      Object value6,
      long userId,
      Object key) {
    return db.update(
        "INSERT IGNORE INTO"
            + " eam_home_template(id,tenant_id,name,draft,role_ids,priority,created_by,preset_key)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        tid,
        tenantId,
        value,
        payload,
        payload5,
        value6,
        userId,
        key);
  }

  public int insertPresetVersion(
      long tenantId, Object tid, String payload, String payload4, Object value, long userId) {
    return db.update(
        "INSERT INTO"
            + " eam_home_template_version(tenant_id,template_id,version,payload,role_ids,priority,published_by)"
            + " VALUES(?,?,1,?,?,?,?)",
        tenantId,
        tid,
        payload,
        payload4,
        value,
        userId);
  }

  public int publishPreset(long tenantId, Object tid) {
    return db.update(
        "UPDATE eam_home_template SET published_version=1 WHERE tenant_id=? AND id=?",
        tenantId,
        tid);
  }

  public List<Map<String, Object>> listTemplates(long tenantId) {
    return db.queryForList(
        "SELECT * FROM eam_home_template WHERE tenant_id=? ORDER BY priority,id", tenantId);
  }

  public List<Map<String, Object>> loadTemplate(long tenantId, Object id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM eam_home_template WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
        tenantId,
        id);
  }

  public int countActiveRole(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_sys_role WHERE tenant_id=? AND id=? AND status=1",
        Integer.class,
        tenantId,
        value);
  }

  public int insertTemplate(
      Object tid,
      long tenantId,
      Object name,
      String payload,
      String payload5,
      Object priority,
      long userId) {
    return db.update(
        "INSERT INTO eam_home_template(id,tenant_id,name,draft,role_ids,priority,created_by)"
            + " VALUES(?,?,?,?,?,?,?)",
        tid,
        tenantId,
        name,
        payload,
        payload5,
        priority,
        userId);
  }

  public int updateTemplate(
      Object name, String payload, String payload3, Object priority, long tenantId, Object tid) {
    return db.update(
        "UPDATE eam_home_template SET"
            + " name=?,draft=?,role_ids=?,priority=?,row_version=row_version+1,updated_at=NOW(3)"
            + " WHERE tenant_id=? AND id=?",
        name,
        payload,
        payload3,
        priority,
        tenantId,
        tid);
  }

  public void overviewDraft(long tenant, Object template, String payload) {
    db.update(
        "UPDATE eam_home_template SET overview_draft=? WHERE tenant_id=? AND id=?",
        payload,
        tenant,
        template);
  }

  public int insertVersion(
      long tenantId,
      Object tid,
      Object next,
      String payload,
      Object roleIds,
      Object priority,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " eam_home_template_version(tenant_id,template_id,version,payload,role_ids,priority,published_by)"
            + " VALUES(?,?,?,?,?,?,?)",
        tenantId,
        tid,
        next,
        payload,
        roleIds,
        priority,
        userId);
  }

  public int publishVersion(Object next, long tenantId, Object tid) {
    return db.update(
        "UPDATE eam_home_template SET published_version=?,enabled=1,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        next,
        tenantId,
        tid);
  }

  public int disableTemplate(long tenantId, Object tid) {
    return db.update(
        "UPDATE eam_home_template SET enabled=0,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        tid);
  }

  public List<Map<String, Object>> listUserRoles(long tenantId, long userId) {
    return db.queryForList(
        "SELECT CAST(ur.role_id AS CHAR) id FROM eam_sys_user_role ur JOIN eam_sys_role r"
            + " ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id WHERE ur.tenant_id=? AND"
            + " ur.user_id=? AND r.status=1",
        tenantId,
        userId);
  }

  public List<Map<String, Object>> listPublishedTemplates(long tenantId) {
    return db.queryForList(
        "SELECT t.id,t.name,t.published_version,v.payload,v.role_ids,v.priority FROM"
            + " eam_home_template t JOIN eam_home_template_version v ON v.tenant_id=t.tenant_id"
            + " AND v.template_id=t.id AND v.version=t.published_version WHERE t.tenant_id=?"
            + " AND t.enabled=1 ORDER BY v.priority,t.id",
        tenantId);
  }

  public List<Map<String, Object>> loadDefaultTemplate(long tenantId, long userId) {
    return db.queryForList(
        "SELECT default_template_id FROM eam_home_preference WHERE tenant_id=? AND user_id=?",
        tenantId,
        userId);
  }

  public List<Map<String, Object>> loadLayout(long tenantId, long userId, Object tid) {
    return db.queryForList(
        "SELECT * FROM eam_home_layout WHERE tenant_id=? AND user_id=? AND template_id=?",
        tenantId,
        userId,
        tid);
  }

  public int countTemplateVersion(long tenantId, Object tid, Object templateVersion) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_home_template_version WHERE tenant_id=? AND"
            + " template_id=? AND version=?",
        Integer.class,
        tenantId,
        tid,
        templateVersion);
  }

  public int insertLayout(
      long tenantId, long userId, Object tid, Object templateVersion, String payload) {
    return db.update(
        "INSERT INTO eam_home_layout(tenant_id,user_id,template_id,template_version,payload)"
            + " VALUES(?,?,?,?,?)",
        tenantId,
        userId,
        tid,
        templateVersion,
        payload);
  }

  public int updateLayout(
      Object templateVersion,
      String payload,
      long tenantId,
      long userId,
      Object tid,
      Object version) {
    return db.update(
        "UPDATE eam_home_layout SET"
            + " template_version=?,payload=?,row_version=row_version+1,updated_at=NOW(3)"
            + " WHERE tenant_id=? AND user_id=? AND template_id=? AND row_version=?",
        templateVersion,
        payload,
        tenantId,
        userId,
        tid,
        version);
  }

  public int resetLayout(long tenantId, long userId, Object tid) {
    return db.update(
        "DELETE FROM eam_home_layout WHERE tenant_id=? AND user_id=? AND template_id=?",
        tenantId,
        userId,
        tid);
  }

  public int saveDefaultTemplate(long tenantId, long userId, Object tid) {
    return db.update(
        "INSERT INTO eam_home_preference(tenant_id,user_id,default_template_id) VALUES(?,?,?) ON"
            + " DUPLICATE KEY UPDATE default_template_id=VALUES(default_template_id)",
        tenantId,
        userId,
        tid);
  }

  public List<Map<String, Object>> listRoleChoices(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name FROM eam_sys_role WHERE tenant_id=? AND status=1 AND"
            + " deleted=0 ORDER BY name,id",
        tenantId);
  }
}
