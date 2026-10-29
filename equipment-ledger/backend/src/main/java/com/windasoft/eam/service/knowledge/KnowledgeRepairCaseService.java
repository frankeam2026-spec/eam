package com.windasoft.eam.service.knowledge;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KnowledgeRepairCaseService {
  private final RepairWorkflowService repair;private final KnowledgeDocumentRepository docs;private final KnowledgeContentWriter writer;private final AuthContext auth;private final PermissionService permissions;private final TransactionTemplate tx;
  public KnowledgeRepairCaseService(RepairWorkflowService repair,KnowledgeDocumentRepository docs,KnowledgeContentWriter writer,AuthContext auth,PermissionService permissions,TransactionTemplate tx){this.repair=repair;this.docs=docs;this.writer=writer;this.auth=auth;this.permissions=permissions;this.tx=tx;}
  private RepairOrder order(long id){var result=repair.detail(id);var order=(RepairOrder)result.get("order");if(!"已完成".equals(order.getStatus())||order.getCompanyOrgId()==null||order.getEquipmentId()==null)throw new IllegalArgumentException("仅可从所属公司和设备明确的已验收工单建立案例");var scope=permissions.scope("knowledge","编辑");if(!scope.all()&&!scope.companyIds().contains(order.getCompanyOrgId()))throw new IllegalArgumentException("无权维护此公司知识库");return order;}
  public Object draft(long id){var order=order(id);String text="设备："+order.getEquipmentCode()+" "+order.getEquipmentName()+"\n故障现象："+Objects.toString(order.getDescription(),"")+"\n确认原因："+Objects.toString(order.getFaultCause(),"未填写，请核对")+"\n实际处理："+Objects.toString(order.getRepairAction(),"未填写，请核对");return Map.of("text",text,"rowVersion",order.getRowVersion(),"title",order.getEquipmentName()+"维修案例");}
  public Object create(long repairId,long repairVersion,String title,String text){
    if(title==null||title.isBlank()||title.length()>200||text==null||text.isBlank()||text.length()>20000)throw new IllegalArgumentException("案例标题或内容无效");
    final String digest;try{digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    return tx.execute(s->{var order=order(repairId);if(order.getRowVersion()!=repairVersion)throw new IllegalArgumentException("工单已修改，请重新生成案例草稿");
      long id=IdWorker.getId(),version=IdWorker.getId(),tenant=auth.tenantId();
      docs.create(tenant,auth.userId(),id,version,order.getCompanyOrgId(),title,"case-"+UUID.randomUUID(),digest,"confirmed-case-v1");
      docs.caseSource(tenant,id,version,repairId,repairVersion);writer.commit(tenant,version,List.of(new KnowledgePdfParser.Page(1,text,false,KnowledgePdfParser.split(text))));
      docs.bind(tenant,id,0,false,List.of(order.getEquipmentId()));return Map.of("id",Long.toString(id),"rowVersion",1);
    });
  }
}
