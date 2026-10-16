package com.windasoft.eam.dto.equipment;

import java.nio.file.Path;

public record EquipmentDownload(Path path, String contentType, String fileName) {}
