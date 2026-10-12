package com.windasoft.eam.repair;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

/** Fixed, parameterized workflow queries. No mapper XML or user supplied SQL. */
@Mapper
public interface RepairWorkflowMapper {
    @Select("SELECT (EXISTS(SELECT 1 FROM eam_repair_order WHERE tenant_id=#{tenant} AND deleted=0 AND (reporter_user_id=#{userId} OR assignee_user_id=#{userId})) OR EXISTS(SELECT 1 FROM eam_repair_candidate WHERE tenant_id=#{tenant} AND user_id=#{userId} AND status='待接单'))")
    boolean personalAccess(long tenant,long userId);
    @Select("SELECT * FROM eam_repair_order WHERE tenant_id=#{tenant} AND id=#{id} AND deleted=0 FOR UPDATE")
    RepairOrder lock(long tenant,long id);
    @Select("SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'') department,(SELECT COUNT(*) FROM eam_repair_order r WHERE r.tenant_id=u.tenant_id AND r.deleted=0 AND r.assignee_user_id=u.id AND r.status IN ('处理中','挂起','待验收')) unfinished FROM eam_sys_user u LEFT JOIN eam_org_unit o ON o.id=u.org_id AND o.tenant_id=u.tenant_id WHERE u.tenant_id=#{tenant} AND u.status=1 AND u.deleted=0 ORDER BY unfinished,u.display_name,u.id")
    List<Map<String,Object>> users(long tenant);
    @Select("SELECT COUNT(*) FROM eam_repair_candidate WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND user_id=#{userId} AND status='待接单'")
    int candidate(long tenant,long orderId,long userId);
    @Update("UPDATE eam_repair_order SET assignee_user_id=NULL WHERE tenant_id=#{tenant} AND id=#{id}")
    void clearOwner(long tenant,long id);
    @Update("UPDATE eam_repair_inbox SET done=1 WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND is_todo=1 AND kind='待出库' AND done=0")
    void closeIssueTodos(long tenant,long orderId);
    @Select("SELECT CAST(c.user_id AS CHAR) id,u.display_name name,c.status FROM eam_repair_candidate c JOIN eam_sys_user u ON c.user_id=u.id AND c.tenant_id=u.tenant_id WHERE c.tenant_id=#{tenant} AND c.order_id=#{orderId}")
    List<Map<String,Object>> candidates(long tenant,long orderId);
    @Delete("DELETE FROM eam_repair_candidate WHERE tenant_id=#{tenant} AND order_id=#{orderId}")
    void clearCandidates(long tenant,long orderId);
    @Insert("INSERT INTO eam_repair_candidate(id,tenant_id,order_id,user_id,status,created_at) VALUES(#{id},#{tenant},#{orderId},#{userId},'待接单',NOW())")
    void addCandidate(long id,long tenant,long orderId,long userId);
    @Update("UPDATE eam_repair_candidate SET status=IF(user_id=#{userId},'已接单','已由他人接单') WHERE tenant_id=#{tenant} AND order_id=#{orderId}")
    void claim(long tenant,long orderId,long userId);
    @Insert("INSERT INTO eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at) VALUES(#{id},#{tenant},#{orderId},#{action},#{actorId},#{actorName},#{details},NOW())")
    void event(long id,long tenant,long orderId,String action,long actorId,String actorName,String details);
    @Select("SELECT CAST(id AS CHAR) id,action,actor_name actor,details_json details,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') time FROM eam_repair_event WHERE tenant_id=#{tenant} AND order_id=#{orderId} ORDER BY created_at DESC,id DESC")
    List<Map<String,Object>> events(long tenant,long orderId);
    @Insert("INSERT INTO eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,created_at) VALUES(#{id},#{tenant},#{userId},#{orderId},#{kind},#{title},#{todo},NOW())")
    void inbox(long id,long tenant,long userId,long orderId,String kind,String title,boolean todo);
    @Update("UPDATE eam_repair_inbox SET done=1 WHERE tenant_id=#{tenant} AND order_id=#{orderId} AND is_todo=1 AND kind<>'待出库' AND done=0")
    void closeTodos(long tenant,long orderId);
    @Select("SELECT CAST(i.id AS CHAR) id,CAST(i.order_id AS CHAR) orderId,i.kind,i.title,i.is_todo todo,i.done,i.read_at IS NOT NULL isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time,r.order_no orderNo,r.equipment_name equipmentName,r.status FROM eam_repair_inbox i JOIN eam_repair_order r ON r.id=i.order_id AND r.tenant_id=i.tenant_id WHERE i.tenant_id=#{tenant} AND i.user_id=#{userId} AND r.deleted=0 ORDER BY i.created_at DESC,i.id DESC LIMIT 300")
    List<Map<String,Object>> inboxRows(long tenant,long userId);
    @Update("UPDATE eam_repair_inbox SET read_at=NOW() WHERE tenant_id=#{tenant} AND user_id=#{userId} AND id=#{id}")
    int markRead(long tenant,long userId,long id);
    @Select("SELECT COUNT(*) FROM eam_repair_inbox WHERE tenant_id=#{tenant} AND user_id=#{userId} AND order_id=#{orderId}")
    int hasInbox(long tenant,long userId,long orderId);
    @Select("SELECT CAST(s.id AS CHAR) id,s.code,s.name,s.model,s.quantity FROM eam_spare_stock s WHERE s.tenant_id=#{tenant} AND s.company_id=#{company} ORDER BY s.code")
    List<Map<String,Object>> stocks(long tenant,long company);
    @Insert("INSERT INTO eam_spare_stock(id,tenant_id,company_id,code,name,model,quantity) VALUES(#{id},#{tenant},#{company},#{code},#{name},#{model},#{quantity})")
    void addStock(long id,long tenant,long company,String code,String name,String model,BigDecimal quantity);
    @Select("SELECT * FROM eam_spare_stock WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    Map<String,Object> lockStock(long tenant,long id);
    @Update("UPDATE eam_spare_stock SET quantity=quantity-#{quantity} WHERE tenant_id=#{tenant} AND id=#{id} AND quantity>=#{quantity}")
    int issueStock(long tenant,long id,BigDecimal quantity);
    @Insert("INSERT INTO eam_repair_material(id,tenant_id,order_id,stock_id,applicant_id,quantity,created_at) VALUES(#{id},#{tenant},#{orderId},#{stockId},#{userId},#{quantity},NOW())")
    void material(long id,long tenant,long orderId,long stockId,long userId,BigDecimal quantity);
    @Select("SELECT CAST(m.id AS CHAR) id,CAST(m.stock_id AS CHAR) stockId,s.code,s.name,s.model,m.quantity,m.issued_quantity issuedQuantity,m.installed_quantity installedQuantity,m.status FROM eam_repair_material m JOIN eam_spare_stock s ON s.id=m.stock_id AND s.tenant_id=m.tenant_id WHERE m.tenant_id=#{tenant} AND m.order_id=#{orderId} ORDER BY m.created_at DESC,m.id DESC")
    List<Map<String,Object>> materials(long tenant,long orderId);
    @Update("UPDATE eam_repair_material SET status='已撤回' WHERE tenant_id=#{tenant} AND id=#{id} AND order_id=#{orderId} AND status='待出库'")
    int cancelMaterial(long tenant,long id,long orderId);
    @Select("SELECT * FROM eam_repair_material WHERE tenant_id=#{tenant} AND id=#{id} AND order_id=#{orderId} FOR UPDATE")
    Map<String,Object> lockMaterial(long tenant,long id,long orderId);
    @Update("UPDATE eam_repair_material SET status='已出库',issued_quantity=quantity,issued_at=NOW(),issuer_id=#{userId} WHERE tenant_id=#{tenant} AND id=#{id} AND status='待出库'")
    int issued(long tenant,long id,long userId);
    @Update("UPDATE eam_repair_material SET installed_quantity=installed_quantity+#{quantity} WHERE tenant_id=#{tenant} AND id=#{id} AND issued_quantity-installed_quantity>=#{quantity}")
    int consume(long tenant,long id,BigDecimal quantity);
    @Insert("INSERT INTO eam_equipment_spare(id,tenant_id,equipment_id,stock_id,order_id,quantity,installed_at) VALUES(#{id},#{tenant},#{equipmentId},#{stockId},#{orderId},#{quantity},NOW())")
    void install(long id,long tenant,long equipmentId,long stockId,long orderId,BigDecimal quantity);
    @Select("SELECT CAST(p.id AS CHAR) id,s.code,s.name,s.model,p.quantity,p.status,DATE_FORMAT(p.installed_at,'%Y-%m-%d %H:%i') installedAt FROM eam_equipment_spare p JOIN eam_spare_stock s ON s.id=p.stock_id AND s.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenant} AND p.equipment_id=#{equipmentId} AND p.status='在用' ORDER BY p.installed_at DESC,p.id DESC")
    List<Map<String,Object>> installed(long tenant,long equipmentId);
    @Update("UPDATE eam_equipment_spare SET status='已换下',removed_at=NOW() WHERE tenant_id=#{tenant} AND equipment_id=#{equipmentId} AND id=#{id} AND status='在用'")
    int remove(long tenant,long equipmentId,long id);
    @Insert("INSERT INTO eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result) VALUES(#{id},#{tenant},#{equipmentId},'SPARE_CHANGE',#{number},NOW(),#{description},#{actor},#{status},#{result})")
    void history(long id,long tenant,long equipmentId,String number,String description,String actor,String status,String result);
}
