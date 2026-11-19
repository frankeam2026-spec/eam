package com.windasoft.eam.dto.knowledge;

import java.util.List;

public final class RepairAssistViews {
  private RepairAssistViews() {}

  public record Citation(
      String chunkId,
      String documentId,
      String versionId,
      String title,
      Integer pageNo,
      String excerpt,
      Boolean hasOriginal,
      String repairOrderId) {}

  public record Draft(String text, List<String> missingInfo, List<Citation> citations) {}

  public record Transcript(String text) {}
}
