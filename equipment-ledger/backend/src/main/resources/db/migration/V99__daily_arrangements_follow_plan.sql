-- Plan enabled is now the single switch. Legacy arrangements are retained, not deleted.
-- Invalidate stale plan drafts before normalizing formerly disabled arrangements.
UPDATE eam_inspection_daily_plan p
 SET p.row_version=p.row_version+1,p.updated_at=CURRENT_TIMESTAMP
 WHERE EXISTS(SELECT 1 FROM eam_inspection_daily_arrangement a
   WHERE a.tenant_id=p.tenant_id AND a.plan_id=p.id AND a.enabled=0);
-- Do not backfill shift windows that ended while an arrangement was disabled.
UPDATE eam_inspection_daily_arrangement
 SET enabled=1,effective_from=GREATEST(COALESCE(effective_from,NOW(6)),NOW(6))
 WHERE enabled=0;
