import { currentLocale } from './i18n';
import { tr } from './i18n';
import { request } from './api';
export type Branding = {
  systemName: string;
  logoUrl: string;
  backgroundUrl: string;
  backgroundPosition: string;
};
export type BrandingSettings = Branding & {
  logoFileId: string;
  backgroundFileId: string;
  rowVersion: number;
  updatedAt: string;
};
export const defaultBranding: Branding = {
  systemName: '设备运维平台',
  logoUrl: '/branding/default-logo.png',
  backgroundUrl: '/branding/default-login-background.png',
  backgroundPosition: 'center',
};
export function publicBranding(tenantId: string) {
  return request<{ data: Branding }>('/branding/public?tenantId=' + encodeURIComponent(tenantId));
}
export function getBrandingSettings() {
  return request<{ data: BrandingSettings }>('/personalization');
}
export function saveBrandingSettings(settings: BrandingSettings) {
  return request<{ data: BrandingSettings }>('/personalization', {
    method: 'PUT',
    body: JSON.stringify(settings),
  });
}
export async function uploadBrandingImage(file: File, kind: 'logo' | 'background') {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch('/api/v1/personalization/assets?kind=' + kind, {
    method: 'POST',
    body: form,
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || tr('ui.validation.message3'));
  return body as { data: { id: string; url: string } };
}

export function discardBrandingUpload(id: string) {
  return request('/personalization/assets/' + encodeURIComponent(id), { method: 'DELETE' });
}
