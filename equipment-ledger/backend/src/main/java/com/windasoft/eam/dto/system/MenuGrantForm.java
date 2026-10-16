package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record MenuGrantForm(List<String> actions, List<String> dataScopes, List<Long> orgIds) {}
