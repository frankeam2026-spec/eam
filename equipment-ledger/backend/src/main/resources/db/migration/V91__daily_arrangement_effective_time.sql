-- A new or edited arrangement must not create already expired historical windows.
ALTER TABLE eam_inspection_daily_arrangement
 ADD COLUMN effective_from DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本版安排生效时间，已结束窗口不追溯生成';
