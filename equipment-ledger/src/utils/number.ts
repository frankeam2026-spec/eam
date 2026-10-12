/** Business values use two decimal places; IDs and counts are not formatted.
 * Round decimal strings directly to preserve large amounts and decimal ties.
 * This is presentation only and must never be used for inventory calculations.
 */
export function formatNumber(value: unknown): string {
  if (value == null || value === '') return '';
  const text = String(value).trim();
  const match = text.match(/^([+-]?)(\d+)(?:\.(\d*))?$/);
  if (!match) {
    const numeric = Number(text);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : text;
  }
  const fraction = match[3] || '';
  let cents = BigInt(match[2]!) * 100n + BigInt(fraction.slice(0, 2).padEnd(2, '0'));
  if (fraction.length > 2 && fraction[2]! >= '5') cents += 1n;
  return `${match[1] === '-' && cents !== 0n ? '-' : ''}${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`;
}

export function isDecimalField(key: string): boolean {
  return /(?:quantity|amount|price|cost|fee|limit|minimum|maximum)$/i.test(key);
}
