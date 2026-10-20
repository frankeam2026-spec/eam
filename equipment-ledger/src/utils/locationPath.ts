export type LocationNode = { id: string; name: string; parentId?: string };
/** 兼容历史位置快照，统一使用“ > ”展示和回填。 */
export function normalizeLocationPath(value: unknown): string {
  return String(value ?? '')
    .split(/[·>]/)
    .map((part) => part.trim())
    .join(' > ');
}
export function locationPath(node: LocationNode, nodes: LocationNode[]): string {
  const parts: string[] = [];
  const visited = new Set<string>();
  let current: LocationNode | undefined = node;
  while (current && !visited.has(String(current.id))) {
    visited.add(String(current.id));
    parts.unshift(current.name);
    current = nodes.find((item) => String(item.id) === String(current!.parentId));
  }
  return normalizeLocationPath(parts.join(' > '));
}
