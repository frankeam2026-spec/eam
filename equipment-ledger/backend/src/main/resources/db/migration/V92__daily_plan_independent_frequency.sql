-- Daily plan frequency is defined by inspection arrangements.
-- Release the obsolete plan-level dictionary reference; task history remains unchanged.
UPDATE eam_inspection_daily_plan SET type_id = NULL WHERE type_id IS NOT NULL;
