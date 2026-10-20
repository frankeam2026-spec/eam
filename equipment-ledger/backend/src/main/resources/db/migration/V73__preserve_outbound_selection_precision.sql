-- Display precision does not reduce the precision of stored inventory quantities.
ALTER TABLE inv_outbound_selection MODIFY quantity DECIMAL(20,6) NOT NULL COMMENT '指定数量，保持库存数量精度';
-- Runs in the same migration window before application traffic. Restore original precision
-- only where the stored value still equals the former two-decimal representation.
UPDATE inv_outbound_selection s
JOIN inv_issue i ON i.tenant_id=s.tenant_id AND i.id=s.issue_id
JOIN migration_v64_outbound_archive a ON a.tenant_id=s.tenant_id AND a.issue_id=s.issue_id
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(a.original_payload,'$.lineSelections'),JSON_ARRAY()),'$[*]' COLUMNS(line_no FOR ORDINALITY,choices JSON PATH '$')) l ON l.line_no=s.line_no
JOIN JSON_TABLE(l.choices,'$[*]' COLUMNS(sort_no FOR ORDINALITY,balance_id BIGINT PATH '$.balanceId',quantity DECIMAL(20,6) PATH '$.quantity')) c ON c.sort_no=s.sort_no+1 AND c.balance_id=s.balance_id
SET s.quantity=c.quantity
WHERE s.quantity=ROUND(c.quantity,2);
