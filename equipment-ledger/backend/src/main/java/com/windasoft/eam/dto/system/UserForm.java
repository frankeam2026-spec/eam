package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserForm(
    @NotBlank String account,
    @NotBlank String name,
    String employeeNo,
    String phone,
    @NotNull @JsonDeserialize(using = ExactLongDeserializer.class) Long orgId,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> roleIds,
    @Min(0) @Max(1) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer status,
    String password,
    @Email @Size(max = 254) String email,
    @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer emailVersion) {
  public UserForm(
      String account,
      String name,
      String employeeNo,
      String phone,
      Long orgId,
      List<Long> roleIds,
      Integer status,
      String password) {
    this(account, name, employeeNo, phone, orgId, roleIds, status, password, null, null);
  }

  public UserForm(
      String account,
      String name,
      String employeeNo,
      String phone,
      Long orgId,
      List<Long> roleIds,
      Integer status,
      String password,
      String email) {
    this(account, name, employeeNo, phone, orgId, roleIds, status, password, email, 0);
  }
}
