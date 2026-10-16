/** Device categories belong to base data; accept the former standalone grant for compatibility. */
export function canUseBaseTab(
  can: (menu: string, action: string) => boolean,
  tab: string,
  action = '查看',
): boolean {
  return can('base', action) || (tab === '设备分类' && can('category', action));
}
