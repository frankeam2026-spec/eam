-- Only untouched schedules adopt the plan start. Generated/deleted task identities remain authoritative.
UPDATE eam_inspection_periodic_plan p JOIN eam_inspection_schedule s
 ON s.tenant_id=p.tenant_id AND s.plan_id=p.id
 SET p.next_at=TIMESTAMP(p.start_date),p.row_version=p.row_version+1,p.updated_at=CURRENT_TIMESTAMP
 WHERE p.start_date IS NOT NULL
 AND (NOT(p.next_at<=>TIMESTAMP(p.start_date)) OR NOT(s.anchor_at<=>TIMESTAMP(p.start_date)) OR NOT(s.next_at<=>TIMESTAMP(p.start_date)))
 AND NOT EXISTS(SELECT 1 FROM eam_inspection_task t WHERE t.tenant_id=p.tenant_id AND t.plan_id=p.id);
UPDATE eam_inspection_schedule s JOIN eam_inspection_periodic_plan p
 ON p.tenant_id=s.tenant_id AND p.id=s.plan_id
 SET s.next_at=TIMESTAMP(p.start_date),s.anchor_at=TIMESTAMP(p.start_date),s.cycle_index=0
 WHERE p.start_date IS NOT NULL
 AND NOT EXISTS(SELECT 1 FROM eam_inspection_task t WHERE t.tenant_id=p.tenant_id AND t.plan_id=p.id);
