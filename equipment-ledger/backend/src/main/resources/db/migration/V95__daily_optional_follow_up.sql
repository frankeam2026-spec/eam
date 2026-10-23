-- Empty optional follow-up owners previously reached nullable("") and became 0.
-- 0 is not a valid user ID; preserve all real associations.
UPDATE eam_inspection_daily_arrangement SET follow_up_user_id=NULL WHERE follow_up_user_id=0;
