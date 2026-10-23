type OrganizationNode = { id: string; name: string; parentId?: string };
/** 兼容旧快照分隔符，仅将完整组织路径统一为展示格式。 */
export function formatOrganizationPath(value: string | undefined): string {
  return (value || '')
    .split(/\s*[·•>]\s*/)
    .map((part) => part.trim())
    .filter(Boolean)
    .join(' > ');
}
export function organizationPath(id: string, nodes: OrganizationNode[]): string {
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const parts: string[] = [];
  const visited = new Set<string>();
  let node = byId.get(id);
  while (node && !visited.has(node.id)) {
    visited.add(node.id);
    parts.unshift(node.name);
    node = byId.get(node.parentId || '');
  }
  return parts.join(' > ');
}
