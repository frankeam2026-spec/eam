package com.windasoft.eam.dto.system;

import java.util.Map;

/** Token is consumed only by the HTTP adapter, never serialized as a login response. */
public record AuthenticationResult(String token, Map<String, Object> user) {}
