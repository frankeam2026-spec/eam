package com.windasoft.eam.common;

public class FieldValidationException extends RuntimeException {
  private final String field;
  private MessageRef messageRef;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public FieldValidationException(String field, MessageRef ref) {
    this(field, ref.defaultText());
    this.messageRef = ref;
  }

  public MessageRef messageRef() {
    return messageRef;
  }

  public String field() {
    return field;
  }
}
