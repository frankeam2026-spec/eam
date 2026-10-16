package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record UserBatchForm(
    @NotNull List<Long> ids, @NotBlank String operation, Long orgId, String password) {}
