import { describe, it, expect } from 'vitest';
import { emptyArrangement } from '../inspection';
import { viewDates, putDate, effectiveEntry, copyPreviousWeek } from '../utils/dailyStaffing';

describe('staffing calendar', () => {
  it('uses local calendar dates across month, year and leap boundaries', () => {
    expect(viewDates('2026-10-23', 'week')).toEqual([
      '2026-10-19',
      '2026-10-20',
      '2026-10-21',
      '2026-10-22',
      '2026-10-23',
      '2026-10-24',
      '2026-10-25',
    ]);
    expect(viewDates('2028-02-12', 'month')).toHaveLength(29);
    expect(viewDates('2027-01-01', 'week')[0]).toBe('2026-12-28');
  });
  it('keeps explicit unstaffed dates ahead of weekday and default rules', () => {
    const a = emptyArrangement('2026-10-23');
    a.regularRoster = [
      {
        key: 'default',
        weekday: 0,
        slotId: '',
        start: '00:00:00',
        end: '00:00:00',
        users: ['11'],
        unassigned: false,
      },
      {
        key: 'fri',
        weekday: 5,
        slotId: '',
        start: '00:00:00',
        end: '00:00:00',
        users: ['12'],
        unassigned: false,
      },
    ];
    expect(effectiveEntry(a, '2026-10-23', '')?.users).toEqual(['12']);
    putDate(a, '2026-10-23', '', [], true);
    expect(effectiveEntry(a, '2026-10-23', '')?.unassigned).toBe(true);
    putDate(a, '2026-10-23', '', ['13']);
    expect(a.dateRoster).toHaveLength(1);
    expect(effectiveEntry(a, '2026-10-23', '')?.users).toEqual(['13']);
  });
  it('copies by weekday without replacing existing overrides or mutating the source', () => {
    const a = emptyArrangement('2026-10-01');
    a.frequency = 'shift';
    a.slots = [{ id: 'day', name: '白班', start: '08:00:00', end: '20:00:00' }];
    putDate(a, '2026-10-16', 'day', ['11']);
    putDate(a, '2026-10-23', 'day', ['12']);
    expect(copyPreviousWeek(a, ['2026-10-23'], false)).toEqual([]);
    const copied = copyPreviousWeek(a, ['2026-10-23'], true);
    expect(copied[0]?.users).toEqual(['11']);
    expect(a.dateRoster[1]?.users).toEqual(['12']);
  });
});
