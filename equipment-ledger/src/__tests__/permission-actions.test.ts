import { expect, it } from 'vitest';
import { PermissionAction } from '../permissionActions';
import { menuByKey, normalizeMenuGrants } from '../rolePermissions';

it('retains the API action codes in the role configuration', () => {
  expect(menuByKey.workspace.actions).toEqual(['查看', '团队工作查看']);
  expect(PermissionAction.VIEW).toBe('查看');
  expect(PermissionAction.VIEW_TEAM_WORK).toBe('团队工作查看');
});

it('preserves the contextual meaning of a legacy create grant', () => {
  const grants = normalizeMenuGrants(null, ['repair.create', 'requisition.create', 'equipment.create']);
  expect(grants.repair.actions).toEqual(['报修']);
  expect(grants.requisition).toBeUndefined();
  expect(grants.equipment.actions).toEqual(['新增']);
});
