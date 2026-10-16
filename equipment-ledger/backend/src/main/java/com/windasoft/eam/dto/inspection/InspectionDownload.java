package com.windasoft.eam.dto.inspection;

import java.nio.file.Path;

public record InspectionDownload(Path path, String contentType, String name) {}
