// Stable category colors preserve meaning when rankings and periods change.
export const analyticsColors = ['#3478f6', '#16b8a6', '#9b6cf4', '#f5a23d', '#ec6c94', '#38b4dc'];
export const analyticsSeries = { reported: '#3478f6', completed: '#16b8a6', material: '#38b4dc' };
export function categoryColor(key: string) {
  let hash = 2166136261;
  for (const c of key) hash = Math.imul(hash ^ c.charCodeAt(0), 16777619);
  hash ^= hash >>> 16;
  hash = Math.imul(hash, 0x85ebca6b);
  hash ^= hash >>> 13;
  return analyticsColors[(hash >>> 0) % analyticsColors.length]!;
}
