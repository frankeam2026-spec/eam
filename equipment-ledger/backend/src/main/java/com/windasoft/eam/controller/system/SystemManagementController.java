package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.UserBatchForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserListView;
import com.windasoft.eam.dto.system.UserStatusForm;
import com.windasoft.eam.dto.system.UserSummary;
import com.windasoft.eam.service.system.OrganizationService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserListService;
import com.windasoft.eam.service.system.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统管理接口：组织、用户和角色的 HTTP 入口；业务授权与状态变更交由服务层。 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemManagementController {
  private final OrganizationService organizations;

  private final UserListService userLists;

  private final UserManagementService users;
  private final RoleManagementService roles;

  public SystemManagementController(
      OrganizationService organizations,
      UserManagementService users,
      RoleManagementService roles,
      UserListService userLists) {
    this.organizations = organizations;
    this.users = users;
    this.roles = roles;
    this.userLists = userLists;
  }

  @GetMapping("/roles/warehouse-choices")
  public Object roleWarehouses() {
    return roles.roleWarehouses();
  }

  @GetMapping("/organizations")
  public Map<String, Object> organizations() {
    return organizations.organizations();
  }

  @PostMapping("/organizations")
  public Map<String, Object> createOrg(@Valid @RequestBody OrgForm f) {
    return organizations.createOrg(f);
  }

  @PutMapping("/organizations/{id}")
  public Map<String, Object> updateOrg(@PathVariable Long id, @Valid @RequestBody OrgForm f) {
    return organizations.updateOrg(id, f);
  }

  @DeleteMapping("/organizations/{id}")
  public Map<String, Object> deleteOrg(@PathVariable Long id) {
    return organizations.deleteOrg(id);
  }

  @GetMapping("/users")
  public PageResult<UserListView> users(@RequestParam Map<String, String> parameters) {
    return userLists.query(ListQuery.from(parameters));
  }

  @GetMapping("/users/summary")
  public UserSummary userSummary() {
    return userLists.summary();
  }

  @GetMapping("/users/export")
  public DataResponse<List<UserListView>> userExport(@RequestParam Map<String, String> parameters) {
    return new DataResponse<>(userLists.export(ListQuery.from(parameters)));
  }

  @PostMapping("/users")
  public Map<String, Object> createUser(@Valid @RequestBody UserForm f) {
    return users.createUser(f);
  }

  @PutMapping("/users/{id}")
  public Map<String, Object> updateUser(@PathVariable Long id, @Valid @RequestBody UserForm f) {
    return users.updateUser(id, f);
  }

  @PatchMapping("/users/{id}/status")
  public Map<String, Object> userStatus(
      @PathVariable Long id, @Valid @RequestBody UserStatusForm body) {
    return users.userStatus(id, body);
  }

  @PostMapping("/users/batch")
  public Map<String, Object> batchUsers(@Valid @RequestBody UserBatchForm f) {
    return users.batchUsers(f);
  }

  @DeleteMapping("/users/{id}")
  public Map<String, Object> deleteUser(@PathVariable Long id) {
    return users.deleteUser(id);
  }

  @GetMapping("/roles")
  public Map<String, Object> roles() {
    return roles.roles();
  }

  @PostMapping("/roles")
  public Map<String, Object> createRole(@Valid @RequestBody RoleForm f) {
    return roles.createRole(f);
  }

  @PutMapping("/roles/{id}")
  public Map<String, Object> updateRole(@PathVariable Long id, @Valid @RequestBody RoleForm f) {
    return roles.updateRole(id, f);
  }

  @DeleteMapping("/roles/{id}")
  public Map<String, Object> deleteRole(@PathVariable Long id) {
    return roles.deleteRole(id);
  }
}
