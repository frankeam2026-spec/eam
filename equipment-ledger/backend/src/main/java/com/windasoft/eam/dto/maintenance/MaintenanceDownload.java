package com.windasoft.eam.dto.maintenance;

import java.nio.file.Path;

public record MaintenanceDownload(Path path, String contentType, String name) {}
