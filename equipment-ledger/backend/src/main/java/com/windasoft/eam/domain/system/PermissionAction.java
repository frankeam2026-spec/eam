package com.windasoft.eam.domain.system;

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
  DISPATCH("派工"), PROCESS("处理"), WITHDRAW("撤回");

  private final String code;

  PermissionAction(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
