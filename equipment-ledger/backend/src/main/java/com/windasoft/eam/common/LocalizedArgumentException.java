package com.windasoft.eam.common;

public class LocalizedArgumentException extends IllegalArgumentException {
  private final MessageRef messageRef;

  public LocalizedArgumentException(String key, Object... args) {
    this(MessageRef.of(key, args));
  }

  private LocalizedArgumentException(MessageRef ref) {
    super(MessageContext.render(ref));
    this.messageRef = ref;
  }

  public LocalizedArgumentException(MessageRef ref, Throwable cause) {
    super(MessageContext.render(ref), cause);
    this.messageRef = ref;
  }

  public MessageRef messageRef() {
    return messageRef;
  }
}
