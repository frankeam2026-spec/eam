package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.inventory.OutboundHeaderData;
import java.sql.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V64__outbound_header_relations extends BaseJavaMigration {
  @Override public void migrate(Context context) throws Exception {
    var c=context.getConnection();var json=new ObjectMapper();
    try(var s=c.createStatement()){
      s.execute("CREATE TABLE migration_v64_outbound_archive (tenant_id BIGINT NOT NULL,issue_id BIGINT NOT NULL,original_payload JSON NOT NULL,archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,issue_id)) COMMENT='V64出库原始数据归档，仅供核对和回退'");
      s.execute("INSERT INTO migration_v64_outbound_archive(tenant_id,issue_id,original_payload) SELECT tenant_id,id,payload FROM inv_issue WHERE payload IS NOT NULL");
      s.execute("ALTER TABLE inv_issue ADD purpose VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '领用用途',ADD remark VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '备注',ADD department_id BIGINT NULL COMMENT '领用部门ID，多来源单据以明细为准',ADD recipient_id BIGINT NULL COMMENT '领用人ID，多来源单据以明细为准',ADD recipient_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '领用人名称快照',ADD task_kind VARCHAR(20) NOT NULL DEFAULT '' COMMENT '关联任务类型',ADD task_id BIGINT NULL COMMENT '关联任务ID',ADD legacy_fingerprint VARCHAR(512) NOT NULL DEFAULT '' COMMENT '旧确认入口的幂等内容指纹',ADD INDEX ix_issue_recipient(tenant_id,recipient_id),ADD INDEX ix_issue_task(tenant_id,task_kind,task_id)");
      s.execute("CREATE TABLE inv_outbound_selection (tenant_id BIGINT NOT NULL COMMENT '租户ID',issue_id BIGINT NOT NULL COMMENT '出库单ID',line_no INT NOT NULL COMMENT '出库明细行号',sort_no INT NOT NULL COMMENT '选择顺序',balance_id BIGINT NOT NULL COMMENT '指定库存余额ID，对应库位和批次',quantity DECIMAL(18,2) NOT NULL COMMENT '指定数量',PRIMARY KEY(tenant_id,issue_id,line_no,sort_no),INDEX ix_outbound_selection_balance(tenant_id,balance_id),CONSTRAINT fk_outbound_selection_issue FOREIGN KEY(issue_id) REFERENCES inv_issue(id),CONSTRAINT ck_outbound_selection_quantity CHECK(quantity>0)) COMMENT='出库草稿指定库位批次选择，非实际扣库记录'");
    }
    try(var s=c.createStatement();var r=s.executeQuery("SELECT tenant_id,id,payload FROM inv_issue WHERE payload IS NOT NULL ORDER BY id")){
      while(r.next()){
        var data=json.readValue(r.getString("payload"),OutboundHeaderData.class);long tenant=r.getLong("tenant_id"),id=r.getLong("id");
        try(var p=c.prepareStatement("UPDATE inv_issue SET purpose=?,remark=?,department_id=?,recipient_id=?,recipient_name=?,task_kind=?,task_id=?,legacy_fingerprint=? WHERE tenant_id=? AND id=?")){
          p.setString(1,text(data.purpose()));p.setString(2,text(data.remark()));p.setObject(3,positive(data.departmentId()));p.setObject(4,positive(data.recipientId()));p.setString(5,text(data.recipientName()));p.setString(6,text(data.taskKind()));p.setObject(7,positive(data.taskId()));p.setString(8,text(data.legacyFingerprint()));p.setLong(9,tenant);p.setLong(10,id);p.executeUpdate();
        }
        if(data.lineSelections()!=null)for(int line=0;line<data.lineSelections().size();line++){
          var choices=data.lineSelections().get(line);if(choices==null)continue;
          for(int at=0;at<choices.size();at++){var choice=choices.get(at);
            try(var p=c.prepareStatement("INSERT INTO inv_outbound_selection VALUES(?,?,?,?,?,?)")){p.setLong(1,tenant);p.setLong(2,id);p.setInt(3,line+1);p.setInt(4,at);p.setObject(5,choice.balanceId());p.setBigDecimal(6,choice.quantity());p.executeUpdate();}
          }
        }
      }
    }
    try(var s=c.createStatement()){
      s.execute("UPDATE inv_issue i JOIN eam_sys_user u ON u.tenant_id=i.tenant_id AND u.id=i.recipient_id SET i.recipient_name=u.display_name WHERE i.recipient_name=''");
      s.execute("ALTER TABLE inv_issue DROP COLUMN payload");
    }
  }
  private static String text(String value){return value==null?"":value;}
  private static Long positive(Long value){return value==null||value==0?null:value;}
}
