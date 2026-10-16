package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record OrgForm(
    @NotBlank String name,
    @NotBlank String code,
    @NotBlank String type,
    Long parentId,
    String manager,
    Integer status) {}
