package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.UserBatchForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.service.system.OrganizationService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserManagementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
public class SystemManagementController {
  private final OrganizationService organizations;
  private final UserManagementService users;
  private final RoleManagementService roles;

  public SystemManagementController(
      OrganizationService organizations, UserManagementService users, RoleManagementService roles) {
    this.organizations = organizations;
    this.users = users;
    this.roles = roles;
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
  public Map<String, Object> users() {
    return users.users();
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
      @PathVariable Long id, @RequestBody Map<String, Integer> body) {
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
