import { beforeEach, describe, expect, it } from 'vitest';
import { effectScope, ref } from 'vue';
import { useEquipmentPageSize } from '../composables/equipment/useEquipmentPageSize';

beforeEach(() => localStorage.clear());
describe('equipment page-size preferences', () => {
  it('restores only the current account preference and defaults new accounts to 20', () => {
    localStorage.setItem('eam-equipment-page-size-t1-u1', '50');
    const account = ref('t1-u1');
    const scope = effectScope();
    const size = scope.run(() => useEquipmentPageSize(account))!;
    expect(size.value).toBe(50);
    account.value = 't1-u2';
    expect(size.value).toBe(20);
    size.value = 100;
    account.value = 't1-u1';
    expect(size.value).toBe(50);
    expect(localStorage.getItem('eam-equipment-page-size-t1-u2')).toBe('100');
    account.value = 't1-u2';
    expect(size.value).toBe(100);
    scope.stop();
  });
  it.each(['10', '999'])('ignores unsupported cached size %s without blocking the list', (saved) => {
    localStorage.setItem('eam-equipment-page-size-t1-u1', saved);
    const scope = effectScope();
    const size = scope.run(() => useEquipmentPageSize('t1-u1'))!;
    expect(size.value).toBe(20);
    size.value = Number.NaN;
    expect(size.value).toBe(20);
    scope.stop();
  });
});
