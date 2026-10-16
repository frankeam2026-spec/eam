package com.windasoft.eam.dto.experience;

import java.io.InputStream;

public record AnomalyUpload(
    String name, String contentType, long size, boolean empty, InputStream content) {}
