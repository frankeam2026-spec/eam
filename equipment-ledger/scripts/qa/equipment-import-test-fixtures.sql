-- Isolated acceptance fixture only. This file must never run on eam_demo or a customer database.
USE eam_import_test_20261018;
DELIMITER $$
DROP TRIGGER IF EXISTS qa_import_rollback$$
CREATE TRIGGER qa_import_rollback BEFORE INSERT ON eam_equipment FOR EACH ROW
BEGIN
 IF NEW.name='隔离回滚触发' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated import rollback fixture'; END IF;
END$$
DELIMITER ;
