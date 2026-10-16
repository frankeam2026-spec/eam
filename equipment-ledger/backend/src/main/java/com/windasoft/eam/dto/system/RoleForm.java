package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record RoleForm(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    Integer status,
    @NotNull Map<String, MenuGrantForm> menuPermissions) {}
