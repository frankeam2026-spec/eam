import { request } from './api';
import type { SelectorPage, SelectorRow } from './selectors';
export type ReminderKind = 'repair' | 'daily' | 'periodic' | 'maintenance';
export const reminderKinds: ReminderKind[] = ['repair', 'daily', 'periodic', 'maintenance'];
export type ReminderRule = {
  id?: string;
  scene: string;
  value: number;
  unit: string;
  enabled: boolean;
  recipients: string[];
  cc: string[];
};
export type ReminderPolicy = {
  id?: string;
  name: string;
  businessKind: ReminderKind;
  enabled: boolean;
  rowVersion?: number;
  rules: ReminderRule[];
  people?: { userId: string; name: string }[];
};
export type DefaultPolicy = {
  businessKind: ReminderKind;
  policyId: string | null;
  rowVersion: number;
};
export type NotificationChannels = {
  rowVersion: number;
  systemEnabled: boolean;
  emailEnabled: boolean;
  smtpHost: string;
  smtpPort: number;
  smtpSecurity: string;
  smtpUsername: string;
  password?: string;
  hasPassword?: boolean;
  senderAddress: string;
  publicUrl: string;
  appStatus: string;
};
export type Delivery = {
  id: string;
  channel: string;
  status: string;
  attempts: number;
  lastError?: string;
  subject: string;
  recipient: string;
  recipientKind: string;
  module: string;
  businessKind?: string;
  createdAt: string;
  sentAt?: string;
  readAt?: string;
};
export type DeliveryFilters = {
  from: string;
  to: string;
  businessKind: string;
  recipientId: string;
  showInactive: boolean;
};
export type DeliveryRetention = { rowVersion: number; successDays: number; exceptionDays: number };
const qs = (params: Record<string, string | number | boolean>) =>
  new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString();
export const notificationApi = {
  policies: (kind: string, q: string, page: number, size: number) =>
    request<{ data: ReminderPolicy[]; total: number }>(
      '/notifications/policies?' + qs({ kind, q, page, size }),
    ),
  policy: (id: string) => request<{ data: ReminderPolicy }>('/notifications/policies/' + id),
  savePolicy: (policy: ReminderPolicy) =>
    request<{ data: ReminderPolicy }>('/notifications/policies', {
      method: 'POST',
      body: JSON.stringify(policy),
    }),
  defaults: () => request<{ data: DefaultPolicy[] }>('/notifications/defaults'),
  setDefault: (kind: ReminderKind, policyId: string | null, rowVersion: number) =>
    request<{ data: DefaultPolicy[] }>('/notifications/defaults/' + kind, {
      method: 'PUT',
      body: JSON.stringify({ policyId, rowVersion }),
    }),
  users: async (q: string, page: number, size: number): Promise<SelectorPage<SelectorRow>> => {
    const result = await request<{ data: SelectorRow[]; total: number }>(
      '/notifications/users?' + qs({ q, page, size }),
    );
    return { data: result.data, page: { total: result.total } };
  },
  choices: async (
    kind: ReminderKind,
    q: string,
    page: number,
    size: number,
  ): Promise<SelectorPage<SelectorRow>> => {
    const result = await request<{ data: ReminderPolicy[]; page: { total: number } }>(
      '/notifications/policies/choices?' + qs({ kind, q, page, size }),
    );
    return {
      data: result.data.map((p) => ({ id: p.id!, name: p.name, disabled: !p.enabled })),
      page: result.page,
    };
  },
  channels: () => request<{ data: NotificationChannels }>('/notifications/channels'),
  saveChannels: (body: NotificationChannels) =>
    request<{ data: NotificationChannels }>('/notifications/channels', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  testEmail: (address: string) =>
    request('/notifications/channels/test-email', {
      method: 'POST',
      body: JSON.stringify({ address }),
    }),
  deliveries: (
    status: string,
    channel: string,
    q: string,
    page: number,
    size: number,
    filters?: DeliveryFilters,
  ) =>
    request<{ data: Delivery[]; total: number }>(
      '/notifications/deliveries?' + qs({ status, channel, q, page, size, ...filters }),
    ),
  deliveryRecipients: async (
    q: string,
    page: number,
    size: number,
  ): Promise<SelectorPage<SelectorRow>> => {
    const r = await request<{ data: SelectorRow[]; total: number }>(
      '/notifications/deliveries/recipients?' + qs({ q, page, size }),
    );
    return { data: r.data, page: { total: r.total } };
  },
  retention: () => request<{ data: DeliveryRetention }>('/notifications/deliveries/retention'),
  saveRetention: (body: DeliveryRetention) =>
    request<{ data: DeliveryRetention }>('/notifications/deliveries/retention', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  retry: (item: Delivery) =>
    request('/notifications/deliveries/' + item.id + '/retry', {
      method: 'POST',
      body: JSON.stringify({ attempts: item.attempts }),
    }),
};
export function scenes(kind: ReminderKind) {
  return kind === 'repair'
    ? ['WAIT_DISPATCH', 'REPORT_UNCLAIMED', 'WAIT_CLAIM', 'PROCESS', 'WAIT_REVIEW']
    : ['BEFORE_DUE', 'AFTER_DUE', 'WAIT_REVIEW'];
}
