import {describe,it,expect,beforeEach} from 'vitest';
import {effectScope,nextTick,ref} from 'vue';
import {useColumnPreferences} from '../composables/useColumnPreferences';
beforeEach(()=>localStorage.clear());
describe('column preferences',()=>{
 it('keeps the existing keys and separate preferences for each resource',async()=>{
  localStorage.setItem('eam-maintenance-columns-plans','["code","custom:stable-id"]');localStorage.setItem('eam-maintenance-widths-plans','{"code":180}');
  const scope=effectScope(),kind=ref('plans');const preferences=scope.run(()=>useColumnPreferences('eam-maintenance',kind,()=>['code','name']))!;
  expect(preferences.visibleColumns.value).toEqual(['code','custom:stable-id']);expect(preferences.widths.value.code).toBe(180);kind.value='tasks';await nextTick();expect(preferences.visibleColumns.value).toEqual(['code','name']);expect(preferences.widths.value).toEqual({});
  kind.value='plans';await nextTick();expect(preferences.visibleColumns.value).toEqual(['code','custom:stable-id']);scope.stop();
 });
 it('recovers from malformed local preferences without losing the list',()=>{
  localStorage.setItem('eam-inspection-columns-daily-tasks','{broken');localStorage.setItem('eam-inspection-widths-daily-tasks','[100]');const scope=effectScope();const preferences=scope.run(()=>useColumnPreferences('eam-inspection','daily-tasks',['code']))!;expect(preferences.visibleColumns.value).toEqual(['code']);expect(preferences.widths.value).toEqual({});scope.stop();
 });
});
