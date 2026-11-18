package com.windasoft.eam.dto.system;

/** Token is consumed only by the HTTP adapter, never serialized as a login response. */
public record AuthenticationResult(String token, AuthenticatedUserView user) {}
