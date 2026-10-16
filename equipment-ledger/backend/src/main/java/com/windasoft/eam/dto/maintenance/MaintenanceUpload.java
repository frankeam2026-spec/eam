package com.windasoft.eam.dto.maintenance;

import java.io.InputStream;

public record MaintenanceUpload(
    String name, String contentType, long size, boolean empty, InputStream content) {}
