package com.windasoft.eam.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class LocalizedStatusException extends ResponseStatusException {
  private final MessageRef messageRef;

  public LocalizedStatusException(HttpStatusCode status, String key, Object... args) {
    super(status, MessageContext.render(MessageRef.of(key, args)));
    messageRef = MessageRef.of(key, args);
  }

  public MessageRef messageRef() {
    return messageRef;
  }
}
