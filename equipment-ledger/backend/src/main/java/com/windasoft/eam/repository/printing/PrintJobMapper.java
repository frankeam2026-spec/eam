package com.windasoft.eam.repository.printing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.printing.PrintJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrintJobMapper extends BaseMapper<PrintJob> {
  /** 同租户/办理人/请求键唯一约束完成幂等争用；重复请求不改快照，随后锁读原记录核验摘要。 */
  @Insert(
      """
INSERT INTO eam_print_job(id,tenant_id,company_id,warehouse_id,document_id,document_code,document_status,template_id,template_name,template_version,template_snapshot,document_snapshot,contains_money,actor_id,actor_name,request_key,request_hash,pdf_sha256,page_count,pdf_content)
VALUES(#{row.id},#{row.tenantId},#{row.companyId},#{row.warehouseId},#{row.documentId},#{row.documentCode},#{row.documentStatus},#{row.templateId},#{row.templateName},#{row.templateVersion},CAST(#{row.templateSnapshot} AS JSON),CAST(#{row.documentSnapshot} AS JSON),#{row.containsMoney},#{row.actorId},#{row.actorName},#{row.requestKey},#{row.requestHash},#{row.pdfSha256},#{row.pageCount},#{row.pdfContent})
ON DUPLICATE KEY UPDATE id=id
""")
  int insertOnce(@Param("row") PrintJob row);
}
