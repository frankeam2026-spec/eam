import type { DailyArrangement, StaffEntry, DateStaff } from '../inspection';

export const staffKey = () => crypto.randomUUID().replaceAll('-', '');
export const dateString = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
export function addDays(day: string, amount: number) {
  const date = new Date(day + 'T00:00:00');
  date.setDate(date.getDate() + amount);
  return dateString(date);
}
export function viewDates(day: string, mode: 'week' | 'month') {
  const date = new Date(day + 'T00:00:00');
  if (mode === 'week') date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  else date.setDate(1);
  const start = dateString(date),
    length = mode === 'week' ? 7 : new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  return Array.from({ length }, (_, i) => addDays(start, i));
}
export function staffColumns(a: DailyArrangement) {
  return ['shift', 'slots'].includes(a.frequency)
    ? a.slots.map((s) => ({ id: s.id, name: s.name, start: s.start || '', end: s.end }))
    : [{ id: '', name: '', start: '00:00:00', end: '00:00:00' }];
}
export function dateEntry(a: DailyArrangement, date: string, slotId: string) {
  return (a.dateRoster || []).find(
    (r) => r.date === date && r.slotId === slotId && r.start === r.end,
  );
}
export function hasDateOverride(a: DailyArrangement, date: string, slotId: string) {
  return (a.dateRoster || []).some((r) => r.date === date && r.slotId === slotId);
}
export function regularEntry(a: DailyArrangement, date: string, slotId: string) {
  const weekday = new Date(date + 'T00:00:00').getDay() || 7;
  return (
    (a.regularRoster || []).find(
      (r) =>
        r.slotId === slotId &&
        r.weekday === weekday &&
        r.start === '00:00:00' &&
        r.end === '00:00:00',
    ) ||
    (a.regularRoster || []).find((r) => r.slotId === slotId && r.weekday === 0 && r.start === r.end)
  );
}
export function effectiveEntry(
  a: DailyArrangement,
  date: string,
  slotId: string,
): StaffEntry | undefined {
  return dateEntry(a, date, slotId) || regularEntry(a, date, slotId);
}
export function putDate(
  a: DailyArrangement,
  date: string,
  slotId: string,
  users: string[],
  unassigned = false,
) {
  a.dateRoster ??= [];
  a.dateRoster = a.dateRoster.filter(
    (r) => r.date !== date || r.slotId !== slotId || r.start === r.end,
  );
  const prior = dateEntry(a, date, slotId);
  if (prior) {
    prior.users = [...users];
    prior.unassigned = unassigned;
    return;
  }
  a.dateRoster.push({
    key: staffKey(),
    date,
    slotId,
    start: '00:00:00',
    end: '00:00:00',
    users: [...users],
    unassigned,
  });
}
export function copyPreviousWeek(a: DailyArrangement, dates: string[], overwrite: boolean) {
  const updates: DateStaff[] = [];
  for (const date of dates)
    for (const slot of staffColumns(a)) {
      if (!overwrite && hasDateOverride(a, date, slot.id)) continue;
      const source = effectiveEntry(a, addDays(date, -7), slot.id);
      if (source)
        updates.push({
          key: staffKey(),
          date,
          slotId: slot.id,
          start: '00:00:00',
          end: '00:00:00',
          users: [...source.users],
          unassigned: source.unassigned,
        });
    }
  return updates;
}
