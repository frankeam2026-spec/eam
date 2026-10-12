/** Display the API's local business time, preserving its calendar date without UTC conversion. */
export function formatDateTime(value: unknown, empty = ''): string {
  if (value == null || value === '') return empty;
  const text = String(value).trim();
  const match = text.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?/);
  return match ? `${match[1]} ${match[2]}:${match[3] || '00'}` : text;
}
