import { request, requestFile } from './api';
export type ImportState =
  'VALIDATING' | 'INVALID' | 'READY' | 'IMPORTING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
export type ImportBatch = {
  id: string;
  state: ImportState;
  rowVersion: number;
  schemaVersion: number;
  totalRows: number;
  validRows: number;
  errorRows: number;
  errorCount: number;
  autoNumberRows: number;
  successRows: number;
  message: string;
  fileName: string;
  createdAt: string;
  expiresAt: string;
  reportAvailable: boolean;
};
export type ImportIssue = {
  row: number;
  field: string;
  label: string;
  value: string;
  reason: string;
  suggestion: string;
};
export type ImportPreviewRow = {
  row: number;
  values: Record<string, string>;
  issues: ImportIssue[];
};
export type ImportPreview = {
  data: ImportPreviewRow[];
  columns: { key: string; label: string }[];
  page: { number: number; size: number; total: number };
};
export type ImportResultRow = { id: string; code: string; name: string; department: string };
export function importApi(root: string) {
  return {
    template: () => requestFile(`${root}/template`),
    upload: (file: File) => {
      const body = new FormData();
      body.append('file', file);
      return request<ImportBatch>(root, { method: 'POST', body });
    },
    state: (id: string) => request<ImportBatch>(`${root}/${encodeURIComponent(id)}`),
    preview: (id: string, page: number, size: number, errorsOnly: boolean) =>
      request<ImportPreview>(
        `${root}/${encodeURIComponent(id)}/preview?${new URLSearchParams({ page: String(page), size: String(size), errorsOnly: String(errorsOnly) })}`,
      ),
    confirm: (id: string, rowVersion: number, idempotencyKey: string) =>
      request<ImportBatch>(`${root}/${encodeURIComponent(id)}/confirm`, {
        method: 'POST',
        body: JSON.stringify({ rowVersion, idempotencyKey }),
      }),
    report: (id: string) => requestFile(`${root}/${encodeURIComponent(id)}/report`),
    results: (id: string, page: number, size: number) =>
      request<{ data: ImportResultRow[]; page: { number: number; size: number; total: number } }>(
        `${root}/${encodeURIComponent(id)}/results?page=${page}&size=${size}`,
      ),
  };
}
export type ImportApi = ReturnType<typeof importApi>;
