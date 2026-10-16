package com.windasoft.eam.dto.inspection;

import java.io.InputStream;

public record InspectionUpload(
    String name, String contentType, long size, boolean empty, InputStream content) {}
