/** Quantities are database decimals. Keep subtraction exact for display and defaults. */
function units(value: unknown): bigint {
  const match = String(value ?? '0').match(/^(\d+)(?:\.(\d{0,6}))?$/);
  return match ? BigInt(match[1]!) * 1000000n + BigInt((match[2] || '').padEnd(6, '0')) : 0n;
}
export function remainingMaterialQuantity(row: {
  quantity?: unknown;
  usedQuantity?: unknown;
  returnedQuantity?: unknown;
}): string {
  const remaining = units(row.quantity) - units(row.usedQuantity) - units(row.returnedQuantity);
  const value = remaining > 0n ? remaining : 0n;
  return `${value / 1000000n}.${String(value % 1000000n).padStart(6, '0')}`;
}
