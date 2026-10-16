package com.windasoft.eam.dto.repair;

import java.io.InputStream;

public record RepairUpload(
    String originalName, String contentType, long size, boolean empty, InputStream content) {}
