/** Dictionary scope matching is based on IDs, never display names. */
export function standardApplies(
  standard: {
    equipmentIds?: string[];
    categoryIds?: string[];
    categoryNames?: string[];
    legacyCategoryNames?: string[];
  },
  equipment: { id: string; categoryId?: string },
  categories: { id: string; parentId?: string }[],
) {
  const selected = new Set(standard.categoryIds || []);
  const unresolved =
    !!standard.legacyCategoryNames?.length ||
    (standard.categoryIds === undefined && !!standard.categoryNames?.length);
  if (!standard.equipmentIds?.length && !selected.size && !unresolved) return true;
  if (standard.equipmentIds?.includes(equipment.id)) return true;
  const parents = new Map(categories.map((item) => [item.id, item.parentId || '']));
  const seen = new Set<string>();
  let current = equipment.categoryId || '';
  while (current && !seen.has(current)) {
    if (selected.has(current)) return true;
    seen.add(current);
    current = parents.get(current) || '';
  }
  return false;
}
