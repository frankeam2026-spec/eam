export const notificationTabs = [
  { key: 'policies', menu: 'notificationPolicy', label: 'notify.policies' },
  { key: 'channels', menu: 'notificationChannel', label: 'notify.channels' },
  { key: 'deliveries', menu: 'notificationDelivery', label: 'notify.deliveries' },
] as const;

export function visibleNotificationTabs(can: (menu: string) => boolean) {
  return notificationTabs.filter((tab) => can(tab.menu));
}
