package com.windasoft.eam.dto.workflow;

import java.util.List;

/** 流程定义契约；ID使用字符串保持浏览器雪花ID精度，版本保持完整BIGINT。 */
public record WorkflowDefinition(
    String id,
    long rowVersion,
    String name,
    String businessType,
    String companyId,
    String description,
    String versionId,
    String state,
    List<Node> nodes,
    String diagramXml) {
  public WorkflowDefinition(
      String id,
      long rowVersion,
      String name,
      String businessType,
      String companyId,
      String description,
      String versionId,
      String state,
      List<Node> nodes) {
    this(id, rowVersion, name, businessType, companyId, description, versionId, state, nodes, null);
  }

  public record Node(
      String key,
      String type,
      String name,
      String next,
      String approvalMode,
      List<String> users,
      List<String> roles,
      List<Branch> branches,
      String parentKey,
      Script script) {
    public Node(
        String key,
        String type,
        String name,
        String next,
        String approvalMode,
        List<String> users,
        List<String> roles,
        List<Branch> branches) {
      this(key, type, name, next, approvalMode, users, roles, branches, null, null);
    }
  }

  public record Script(String language, String source, String outputKey, String outputType) {}

  public record Branch(
      String key, String name, String target, boolean fallback, String expression) {}
}
