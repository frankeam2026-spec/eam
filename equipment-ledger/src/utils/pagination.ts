export const PAGE_SIZES = [20, 50, 100];
export const DEFAULT_PAGE_SIZE = 20;

/** Restore supported choices only, including when reading legacy preferences or URLs. */
export function normalizePageSize(value: unknown): number {
  const size = Number(value);
  return PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE;
}
