import { describe, it, expect, beforeEach } from 'vitest';
import { effectScope, nextTick } from 'vue';
import { repairListParameters, useRepairColumns } from '../composables/repair/useRepairList';
beforeEach(() => localStorage.clear());
describe('repair list compatibility', () => {
  it('keeps relation separate from field conditions and preserves the search contract', () => {
    const result = repairListParameters(
      { keyword: '泵' },
      [
        { field: 'relation', operator: 'in', values: ['owned', 'created'] },
        { field: 'severity', operator: 'in', values: ['紧急'] },
      ],
      'reportedAt',
      'desc',
    );
    expect(result.relation).toBe('owned,created');
    expect(result.keyword).toBe('泵');
    expect(JSON.parse(result.advanced)).toEqual([
      { field: 'severity', operator: 'in', values: ['紧急'] },
    ]);
  });
  it('preserves the old preference keys and legacy date/status migration', async () => {
    localStorage.setItem('eam-repair-columns', '["orderNo","faultAt"]');
    localStorage.setItem('eam-repair-widths', '{"orderNo":188}');
    const scope = effectScope();
    const prefs = scope.run(() => useRepairColumns(['orderNo', 'status']))!;
    expect(prefs.visibleColumns.value).toEqual(['orderNo', 'reportedAt', 'status']);
    expect(prefs.widths.value.orderNo).toBe(188);
    prefs.widths.value.orderNo = 190;
    await nextTick();
    expect(JSON.parse(localStorage.getItem('eam-repair-widths')!)).toEqual({ orderNo: 190 });
    expect(localStorage.getItem('eam-repair-columns-')).toBeNull();
    scope.stop();
  });
});
