package com.windasoft.eam.domain.system;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 服务端权限动作的既有稳定编码，必须与角色授权配置中的中文动作一致。
 * 动作是否适用于某个菜单仍由 PermissionService 的现有权限矩阵判断。
 */
public enum PermissionAction {
  VIEW("查看"), CREATE("新增"), EDIT("编辑"), DELETE("删除"),
  IMPORT("导入"), EXPORT("导出"), VIEW_AMOUNT("查看金额"),
  EDIT_REFERENCE_PRICE("编辑参考价"), STOCK_IN("入库"), STOCK_OUT("出库"),
  CREATE_DIRECT_OUTBOUND("新增直接出库"), EDIT_DRAFT("编辑草稿"),
  DELETE_DRAFT("删除草稿"), RESERVE("预留"), RELEASE_RESERVATION("释放预留"),
  CONFIRM_ISSUE("确认出库"), CLOSE_REMAINDER("关闭剩余需求"),
  REVERSE("冲销"), FREEZE("冻结"), EXECUTE("执行"), ACCEPT("验收"),
  AMEND_RESULT("修改结果"), REVIEW("审核"), TRANSFER("转派"),
  EXEMPT("免检"), AUTHORIZE("授权"), REPORT("报修"),
  DISPATCH("派工"), PROCESS("处理"), WITHDRAW("撤回"),
  PUBLISH("发布"), DISABLE("停用"), CONFIGURE_SCRIPT("脚本配置"),
  PUBLISH_SCRIPT("脚本发布"), TRIAL_SCRIPT("脚本试算"), VIEW_TEAM_WORK("团队工作查看"),
  RETRY("重试"), RESOLVE_ANOMALY("消除"), SCHEDULE_REPAIR("安排维修"), VOID("作废"),
  ENABLE("启用"), GENERATE_REPAIR_ORDER("生成工单"), CANCEL("取消"),
  RECORD_CUSTODY("登记暂存"), PRINT("打印"), APPLY("申请"), APPROVE("审批"),
  ASSIGN_ROLE("分配角色");

  private static final Map<String, PermissionAction> LEGACY_NAMES = Map.of(
      "view", VIEW, "create", CREATE, "edit", EDIT, "delete", DELETE, "disable", DISABLE,
      "dispatch", DISPATCH, "process", PROCESS, "accept", ACCEPT, "export", EXPORT);

  private final String code;

  PermissionAction(String code) {
    this.code = code;
  }

  /** 权限矩阵使用枚举声明动作，输出仍是角色存储和接口使用的原编码。 */
  public static Set<String> codes(PermissionAction... actions) {
    return Set.of(Arrays.stream(actions).map(PermissionAction::code).toArray(String[]::new));
  }

  /** HTTP 参数沿用原动作编码，各业务入口仍单独限制可选动作。 */
  public static Optional<PermissionAction> findByCode(String code) {
    return Arrays.stream(values()).filter(action -> action.code.equals(code)).findFirst();
  }

  /** 旧角色对外展示保留原来的一对一译名；create 的默认展示是新增。 */
  public static String fromLegacyCode(String action) {
    var known = LEGACY_NAMES.get(action);
    return known == null ? action : known.code();
  }

  /** 旧角色检查同时接受现代动作和旧别名；三个创建入口曾共用 create。 */
  public static String toLegacyCode(String action) {
    if (REPORT.code().equals(action) || APPLY.code().equals(action)) {
      return "create";
    }
    for (var entry : LEGACY_NAMES.entrySet()) {
      if (entry.getValue().code().equals(action)) {
        return entry.getKey();
      }
    }
    return action;
  }

  public String code() {
    return code;
  }
}
