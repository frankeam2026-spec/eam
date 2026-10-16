package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record UserForm(
    @NotBlank String account,
    @NotBlank String name,
    String employeeNo,
    String phone,
    @NotNull Long orgId,
    List<Long> roleIds,
    Integer status,
    String password) {}
