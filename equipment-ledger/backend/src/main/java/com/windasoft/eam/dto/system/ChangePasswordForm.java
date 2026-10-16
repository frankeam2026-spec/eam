package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;

public record ChangePasswordForm(@NotBlank String currentPassword, @NotBlank String newPassword) {}
