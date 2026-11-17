package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.DeletionResult;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.inventory.WarehouseChoice;
import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.dto.system.OrganizationView;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.RoleView;
import com.windasoft.eam.dto.system.UserBatchForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserListView;
import com.windasoft.eam.dto.system.UserStatusForm;
import com.windasoft.eam.dto.system.UserSummary;
import com.windasoft.eam.dto.system.UserView;
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
  public DataResponse<List<WarehouseChoice>> roleWarehouses() {
    return new DataResponse<>(roles.roleWarehouses());
  }

  @GetMapping("/organizations")
  public DataResponse<List<OrganizationView>> organizations() {
    return new DataResponse<>(organizations.organizations());
  }

  @PostMapping("/organizations")
  public DataResponse<OrganizationView> createOrg(@Valid @RequestBody OrgForm f) {
    return new DataResponse<>(organizations.createOrg(f));
  }

  @PutMapping("/organizations/{id}")
  public DataResponse<OrganizationView> updateOrg(
      @PathVariable Long id, @Valid @RequestBody OrgForm f) {
    return new DataResponse<>(organizations.updateOrg(id, f));
  }

  @DeleteMapping("/organizations/{id}")
  public DataResponse<DeletionResult> deleteOrg(@PathVariable Long id) {
    organizations.deleteOrg(id);
    return new DataResponse<>(new DeletionResult(true));
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
  public DataResponse<UserView> createUser(@Valid @RequestBody UserForm f) {
    return new DataResponse<>(users.createUser(f));
  }

  @PutMapping("/users/{id}")
  public DataResponse<UserView> updateUser(@PathVariable Long id, @Valid @RequestBody UserForm f) {
    return new DataResponse<>(users.updateUser(id, f));
  }

  @PatchMapping("/users/{id}/status")
  public DataResponse<UserView> userStatus(
      @PathVariable Long id, @Valid @RequestBody UserStatusForm body) {
    return new DataResponse<>(users.userStatus(id, body));
  }

  @PostMapping("/users/batch")
  public DataResponse<List<UserView>> batchUsers(@Valid @RequestBody UserBatchForm f) {
    return new DataResponse<>(users.batchUsers(f));
  }

  @DeleteMapping("/users/{id}")
  public DataResponse<DeletionResult> deleteUser(@PathVariable Long id) {
    users.deleteUser(id);
    return new DataResponse<>(new DeletionResult(true));
  }

  @GetMapping("/roles")
  public DataResponse<List<RoleView>> roles() {
    return new DataResponse<>(roles.roles());
  }

  @PostMapping("/roles")
  public DataResponse<RoleView> createRole(@Valid @RequestBody RoleForm f) {
    return new DataResponse<>(roles.createRole(f));
  }

  @PutMapping("/roles/{id}")
  public DataResponse<RoleView> updateRole(@PathVariable Long id, @Valid @RequestBody RoleForm f) {
    return new DataResponse<>(roles.updateRole(id, f));
  }

  @DeleteMapping("/roles/{id}")
  public DataResponse<DeletionResult> deleteRole(@PathVariable Long id) {
    roles.deleteRole(id);
    return new DataResponse<>(new DeletionResult(true));
  }
}
