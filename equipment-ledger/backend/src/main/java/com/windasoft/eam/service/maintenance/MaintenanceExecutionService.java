package com.windasoft.eam.service.maintenance;

import com.windasoft.eam.domain.maintenance.MaintenancePersonRole;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.dto.maintenance.MaintenanceFileReference;
import com.windasoft.eam.entity.maintenance.MaintenanceExecution;
import com.windasoft.eam.entity.maintenance.MaintenanceExecutionFile;
import com.windasoft.eam.entity.maintenance.MaintenanceExecutionImage;
import com.windasoft.eam.entity.maintenance.MaintenanceExecutionItem;
import com.windasoft.eam.entity.maintenance.MaintenanceTaskPerson;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionFileRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionImageRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionItemRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskPersonRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在调用方任务事务内替换执行事实及人员关系，不办理任务状态、权限或库存。 生成预填、办理和验收复用同一固定模型；历史快照仍由任务主体维护。 */
@Service
@Transactional
public class MaintenanceExecutionService {
  private static final String EXECUTION_STAGE = "execution";
  private static final String ACCEPTANCE_STAGE = "acceptance";
  private final MaintenanceExecutionRepository executions;
  private final MaintenanceExecutionItemRepository items;
  private final MaintenanceExecutionImageRepository images;
  private final MaintenanceExecutionFileRepository files;
  private final MaintenanceTaskPersonRepository people;

  public MaintenanceExecutionService(
      MaintenanceExecutionRepository executions,
      MaintenanceExecutionItemRepository items,
      MaintenanceExecutionImageRepository images,
      MaintenanceExecutionFileRepository files,
      MaintenanceTaskPersonRepository people) {
    this.executions = executions;
    this.items = items;
    this.images = images;
    this.files = files;
    this.people = people;
  }

  /** 人员数组是关系表的唯一权威；去重保留首次出现顺序，转派不得清除协同人员。 */
  public void participants(long tenant, long task, List<Long> owners, List<Long> reviewers) {
    people.clear(
        tenant,
        task,
        List.of(MaintenancePersonRole.OWNER.code(), MaintenancePersonRole.REVIEWER.code()));
    var rows = new ArrayList<MaintenanceTaskPerson>();
    rows.addAll(personRows(tenant, task, MaintenancePersonRole.OWNER, owners));
    rows.addAll(personRows(tenant, task, MaintenancePersonRole.REVIEWER, reviewers));
    people.insertAll(rows);
  }

  /**
   * 替换须与主体状态及版本推进原子提交。先删除旧执行主体以沿用数据库级联清理， 再清协同人、写主体、项目/图片、阶段附件及协同人；null表示没有执行，空模型表示空执行记录。
   * 项目一次批量写入，图片一次批量写入，不能按项目数量产生额外查询。
   */
  public void save(long tenant, long task, MaintenanceExecutionResult result) {
    executions.delete(tenant, task);
    people.clear(tenant, task, List.of(MaintenancePersonRole.COLLABORATOR.code()));
    if (result == null) {
      return;
    }
    var row = new MaintenanceExecution();
    row.setTenantId(tenant);
    row.setTaskId(task);
    row.setDescription(blankToNull(result.getDescription()));
    row.setStartedAt(result.getStartedAt());
    row.setWorkStartAt(result.getStartAt());
    row.setWorkEndAt(result.getEndAt());
    var acceptance = result.getAcceptance();
    if (acceptance != null) {
      row.setReviewDecision(blankToNull(acceptance.getDecision()));
      row.setReviewDescription(blankToNull(acceptance.getDescription()));
      row.setAcceptanceScore(acceptance.getScore());
      row.setReviewerId(acceptance.getReviewerId());
      row.setReviewerName(blankToNull(acceptance.getReviewer()));
      row.setReviewedAt(acceptance.getReviewedAt());
    }
    executions.insert(row);
    var itemRows = new ArrayList<MaintenanceExecutionItem>();
    var imageRows = new ArrayList<MaintenanceExecutionImage>();
    for (var value : list(result.getItems())) {
      var item = new MaintenanceExecutionItem();
      item.setTenantId(tenant);
      item.setTaskId(task);
      item.setItemKey(text(value.getId()));
      item.setSortNo(itemRows.size());
      item.setResultValue(text(value.getValue()));
      item.setAbnormal(value.isAbnormal());
      item.setResolved(value.isResolved());
      item.setMeasure(text(value.getMeasure()));
      item.setRemark(text(value.getRemark()));
      item.setImplicitNormal(value.isImplicitNormal());
      item.setSkipped(value.isSkipped());
      itemRows.add(item);
      int at = 0;
      for (var file : list(value.getImages())) {
        var image = new MaintenanceExecutionImage();
        image.setTenantId(tenant);
        image.setTaskId(task);
        image.setItemKey(item.getItemKey());
        image.setSortNo(at++);
        image.setFileId(text(file.id()));
        image.setNameSnapshot(text(file.name()));
        image.setContentType(text(file.contentType()));
        image.setSizeBytes(file.size() == null ? 0L : file.size());
        image.setUrlSnapshot(text(file.url()));
        imageRows.add(image);
      }
    }
    items.insertAll(itemRows);
    images.insertAll(imageRows);
    files.insertAll(fileRows(tenant, task, EXECUTION_STAGE, result.getAttachments()));
    if (acceptance != null) {
      files.insertAll(fileRows(tenant, task, ACCEPTANCE_STAGE, acceptance.getAttachments()));
    }
    people.insertAll(
        personRows(tenant, task, MaintenancePersonRole.COLLABORATOR, result.getCollaborators()));
  }

  private List<MaintenanceExecutionFile> fileRows(
      long tenant, long task, String stage, List<MaintenanceFileReference> values) {
    var rows = new ArrayList<MaintenanceExecutionFile>();
    for (var value : list(values)) {
      var file = new MaintenanceExecutionFile();
      file.setTenantId(tenant);
      file.setTaskId(task);
      file.setStageCode(stage);
      file.setSortNo(rows.size());
      file.setFileId(text(value.id()));
      file.setNameSnapshot(text(value.name()));
      file.setContentType(text(value.contentType()));
      file.setSizeBytes(value.size() == null ? 0L : value.size());
      file.setUrlSnapshot(text(value.url()));
      rows.add(file);
    }
    return rows;
  }

  private List<MaintenanceTaskPerson> personRows(
      long tenant, long task, MaintenancePersonRole role, List<Long> users) {
    var rows = new ArrayList<MaintenanceTaskPerson>();
    for (Long user : new LinkedHashSet<>(list(users))) {
      var person = new MaintenanceTaskPerson();
      person.setTenantId(tenant);
      person.setTaskId(task);
      person.setRoleCode(role.code());
      person.setUserId(user);
      person.setSortNo(rows.size());
      rows.add(person);
    }
    return rows;
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static String text(String value) {
    return Objects.toString(value, "");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
