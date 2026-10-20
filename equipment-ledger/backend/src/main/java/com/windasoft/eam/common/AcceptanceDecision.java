package com.windasoft.eam.common;

/** Shared acceptance result; legacy aliases are accepted only at the input boundary. */
public enum AcceptanceDecision {
  ACCEPT("accept", "通过"),
  REJECT("reject", "打回");

  private final String code;
  private final String label;

  AcceptanceDecision(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public String code() {
    return code;
  }

  public String label() {
    return label;
  }

  public static AcceptanceDecision parse(Object value) {
    return switch (java.util.Objects.toString(value, "")) {
      case "accept", "通过" -> ACCEPT;
      case "reject", "return", "打回" -> REJECT;
      default ->
          throw new FieldValidationException(
              "decision", com.windasoft.eam.common.MessageRef.of("server.common.message0031"));
    };
  }
}
