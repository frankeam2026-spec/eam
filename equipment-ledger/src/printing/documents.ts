import { currentLocale } from '../i18n';
import { tr } from '../i18n';
import { ApiError, request } from '../api';
export type PrintColumn = { key: string; label: string; width: number };
export type DocumentPrintConfig = {
  title: string;
  paper: 'A4' | 'A5' | 'CUSTOM';
  landscape: boolean;
  widthMm: number;
  heightMm: number;
  fontSize: number;
  logo: string;
  footer: string;
  headers: string[];
  columns: PrintColumn[];
  signatures: string[];
};
export type DocumentPrintTemplate = {
  id: string;
  name: string;
  companyId: string;
  companyName?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  enabled: boolean;
  isDefault: boolean;
  rowVersion: number;
  config: DocumentPrintConfig;
  updatedAt?: string;
};
export type PrintCatalog = {
  defaults: DocumentPrintConfig;
  headers: Record<string, string>;
  columns: Record<string, string>;
  companies: { id: string; name: string }[];
  canManageCommon: boolean;
};
export type PrintJob = {
  id: string;
  documentCode: string;
  documentStatus: string;
  templateName: string;
  templateVersion: string;
  actorName: string;
  createdAt: string;
  pageCount: string;
};
export type PrintChoice = { id: string; name: string; rowVersion: number; containsMoney: boolean };
export const printing = {
  catalog: () => request<{ data: PrintCatalog }>('/printing/catalog'),
  templates: () => request<{ data: DocumentPrintTemplate[] }>('/printing/templates'),
  save: (value: DocumentPrintTemplate) =>
    request<{ data: DocumentPrintTemplate }>(
      '/printing/templates' + (value.id ? '/' + value.id : ''),
      {
        method: value.id ? 'PUT' : 'POST',
        body: JSON.stringify({ ...value, companyId: value.companyId || null }),
      },
    ),
  remove: (value: DocumentPrintTemplate) =>
    request('/printing/templates/' + value.id + '?rowVersion=' + value.rowVersion, {
      method: 'DELETE',
    }),
  options: (documentId: string) =>
    request<{ data: { templates: PrintChoice[]; defaultId: string } }>(
      '/printing/options?' + new URLSearchParams({ documentId }),
    ),
  preview: (documentId: string, templateId: string) =>
    request<{ data: { pdf: string; fingerprint: string; code: string } }>('/printing/preview', {
      method: 'POST',
      body: JSON.stringify({ documentId, templateId }),
    }),
  generate: (documentId: string, templateId: string, fingerprint: string, requestKey: string) =>
    request<{ data: PrintJob }>('/printing/jobs', {
      method: 'POST',
      body: JSON.stringify({ documentId, templateId, fingerprint, requestKey }),
    }),
  jobs: (documentId: string) =>
    request<{ data: PrintJob[] }>('/printing/jobs?' + new URLSearchParams({ documentId })),
};
export async function printPdf(path: string, init?: RequestInit) {
  const response = await fetch('/api/v1/printing/' + path, {
    ...init,
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value, 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 401) window.dispatchEvent(new Event('eam-session-expired'));
    throw new ApiError(body.message || tr('ui.validation.message38'), response.status);
  }
  return response.blob();
}
export function pdfFromBase64(value: string) {
  const bytes = Uint8Array.from(atob(value), (ch) => ch.charCodeAt(0));
  return new Blob([bytes], { type: 'application/pdf' });
}
export function downloadPrint(url: string, name: string) {
  const a = document.createElement('a');
  a.href = url;
  a.download = name.replace(/[<>:"/\\|?*]/g, '_') + '.pdf';
  a.click();
}
