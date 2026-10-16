package com.windasoft.eam.event.experience;

public final class ExperienceEvents {
  private ExperienceEvents() {}

  public record TaskResult(String kind, long taskId) {}

  public record RepairState(long repairId, String status) {}
}
