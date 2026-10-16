package com.windasoft.eam.dto.equipment;

import java.io.InputStream;

public record EquipmentUpload(String originalName, long size, boolean empty, InputStream content) {}
