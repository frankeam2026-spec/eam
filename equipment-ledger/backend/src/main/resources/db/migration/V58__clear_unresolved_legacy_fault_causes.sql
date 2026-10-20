-- Explicitly approved cleanup of unresolvable legacy dictionary references.
-- Preserve the repair order and its immutable operation history.
UPDATE eam_repair_order
SET fault_cause = '', row_version = row_version + 1
WHERE fault_cause_id IS NULL AND fault_cause <> '';
