/** Accept only the internal task workspace as a return target. */
export function workspaceReturn(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  return value === '/workspace' || value.startsWith('/workspace?') ? value : undefined;
}
