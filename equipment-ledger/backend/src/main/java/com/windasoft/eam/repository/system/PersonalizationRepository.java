package com.windasoft.eam.repository.system;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonalizationRepository {
  private final JdbcTemplate db;

  public PersonalizationRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> configuration(long tenant) {
    return db.queryForList("SELECT * FROM eam_tenant_branding WHERE tenant_id=?", tenant);
  }

  public List<Map<String, Object>> assetOwner(long tenant, String id, String kind) {
    return db.queryForList(
        "SELECT created_by FROM eam_branding_asset WHERE tenant_id=? AND id=? AND kind=?",
        tenant,
        id,
        kind);
  }

  public void ensureConfiguration(long tenant) {
    db.update("INSERT IGNORE INTO eam_tenant_branding(tenant_id) VALUES(?)", tenant);
  }

  public int update(
      String name,
      String logo,
      String background,
      String position,
      long user,
      long tenant,
      int version) {
    return db.update(
        "UPDATE eam_tenant_branding SET"
            + " system_name=?,logo_file_id=?,background_file_id=?,background_position=?,row_version=row_version+1,updated_by=?,updated_at=NOW()"
            + " WHERE tenant_id=? AND row_version=?",
        name,
        logo,
        background,
        position,
        user,
        tenant,
        version);
  }

  public void event(long tenant, long user, String before, String after) {
    db.update(
        "INSERT INTO eam_branding_event(tenant_id,user_id,before_json,after_json) VALUES(?,?,?,?)",
        tenant,
        user,
        before,
        after);
  }

  public void insertAsset(
      String id, long tenant, String kind, String type, byte[] bytes, long user) {
    db.update(
        "INSERT INTO eam_branding_asset(id,tenant_id,kind,content_type,image_data,created_by)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        tenant,
        kind,
        type,
        bytes,
        user);
  }

  public List<Map<String, Object>> asset(long tenant, String id) {
    return db.queryForList(
        "SELECT image_data,content_type,created_by FROM eam_branding_asset WHERE tenant_id=? AND"
            + " id=?",
        tenant,
        id);
  }

  public int discardAsset(long tenant, long user, String id) {
    return db.update(
        "DELETE FROM eam_branding_asset WHERE id=? AND tenant_id=? AND created_by=? AND NOT EXISTS"
            + " (SELECT 1 FROM eam_tenant_branding WHERE tenant_id=? AND (logo_file_id=? OR"
            + " background_file_id=?)) AND NOT EXISTS (SELECT 1 FROM eam_branding_event WHERE"
            + " tenant_id=? AND JSON_SEARCH(after_json,'one',?) IS NOT NULL)",
        id,
        tenant,
        user,
        tenant,
        id,
        id,
        tenant,
        id);
  }
}
