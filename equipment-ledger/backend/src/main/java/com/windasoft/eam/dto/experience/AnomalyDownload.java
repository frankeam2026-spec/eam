package com.windasoft.eam.dto.experience;

import java.nio.file.Path;

public record AnomalyDownload(Path path, String contentType, String fileName) {}
