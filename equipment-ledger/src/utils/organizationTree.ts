/** Build native Element Plus tree data, retaining ancestors supplied by the authorized API. */
export function organizationTree<T extends { id: string; parentId: string }>(rows: T[]) {
  type Node = T & { children: Node[] };
  const map = new Map(rows.map((item) => [item.id, { ...item, children: [] } as Node]));
  const roots: Node[] = [];
  for (const node of map.values()) {
    const parent = map.get(node.parentId);
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  return roots;
}
