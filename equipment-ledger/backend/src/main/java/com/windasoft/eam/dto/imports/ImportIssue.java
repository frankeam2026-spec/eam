package com.windasoft.eam.dto.imports;

public record ImportIssue(
    int row, String field, String label, String value, String reason, String suggestion) {}
