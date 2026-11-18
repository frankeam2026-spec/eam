package com.windasoft.eam.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeViews {
  private KnowledgeViews() {}

  public record Document(
      String id,
      String companyId,
      String title,
      String documentType,
      String status,
      long rowVersion,
      LocalDateTime updatedAt,
      String parseStatus,
      String indexStatus) {}

  public record Documents(List<Document> data, long total) {}

  public record Version(
      String id,
      int versionNo,
      String parseStatus,
      String parserVersion,
      String errorMessage,
      LocalDateTime createdAt,
      Integer published) {}

  public record Versions(List<Version> data, long total) {}

  public record Company(String id, String name) {}

  public record Equipment(String equipmentId) {}

  public record Applicability(boolean all, List<Equipment> equipment, long rowVersion) {}

  public record Page(int pageNo, boolean needsOcr, String textContent, long chunkCount) {}

  public record Uploaded(String id, String versionId, String parseStatus) {}

  public record Replaced(String id, String versionId, long rowVersion) {}

  public record RowVersion(long rowVersion) {}

  public record Status(String status) {}

  public record StatusVersion(String status, long rowVersion) {}

  public record IndexStatus(
      String status, @JsonInclude(JsonInclude.Include.NON_NULL) Integer chunkCount) {}

  public record CaseDraft(String text, int rowVersion, String title) {}

  public record CaseCreated(String id, long rowVersion) {}

  public record IndexCandidate(
      long id,
      long tenantId,
      long versionId,
      String embeddingModel,
      long createdBy,
      Long documentRowVersion,
      long documentId,
      String leaseToken) {}
}
