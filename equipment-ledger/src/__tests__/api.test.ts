import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, uploadRepairFile, requestFile } from '../api';
import { maintenanceApi } from '../maintenance';
import { inspectionApi } from '../inspection';
afterEach(() => vi.unstubAllGlobals());
function response(status: number, payload: object) {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), { status }));
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}
describe('shared HTTP contract', () => {
  it('retains error code, HTTP status and the exact field error path', async () => {
    response(400, {
      code: 'VALIDATION_ERROR',
      message: '请选择单位',
      fieldErrors: { unitId: '请选择单位' },
    });
    await expect(request('/inventory/masters/materials')).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      fieldErrors: { unitId: '请选择单位' },
    });
  });
  it('announces login expiration from maintenance and inspection as well as other APIs', async () => {
    response(401, { message: '请先登录' });
    const expired = vi.fn();
    window.addEventListener('eam-session-expired', expired);
    try {
      await expect(maintenanceApi.tasks()).rejects.toBeInstanceOf(ApiError);
      await expect(inspectionApi.tasks('daily')).rejects.toBeInstanceOf(ApiError);
      expect(expired).toHaveBeenCalledTimes(2);
    } finally {
      window.removeEventListener('eam-session-expired', expired);
    }
  });
  it('passes 409 to the editor without mutating its draft or retrying the write', async () => {
    const fetcher = response(409, { code: 'DATA_CONFLICT', message: '版本已更新' });
    const draft = { id: '1', description: '尚未保存', rowVersion: 2 };
    await expect(
      request('/repairs/1', { method: 'PUT', body: JSON.stringify(draft) }),
    ).rejects.toMatchObject({ status: 409 });
    expect(draft.description).toBe('尚未保存');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('uses the same credentials and error boundary for plan saves', async () => {
    const fetcher = response(200, { data: { id: '123' } });
    await request('/maintenance/plans', { method: 'POST', body: '{}' });
    expect(fetcher).toHaveBeenCalledWith(
      '/api/v1/maintenance/plans',
      expect.objectContaining({ credentials: 'same-origin', method: 'POST' }),
    );
  });
  it('leaves multipart boundaries to the browser and retains the common error contract', async () => {
    const fetcher = response(200, { data: { id: 'file-id' } });
    await uploadRepairFile(new File(['image'], 'image.png', { type: 'image/png' }));
    const options = fetcher.mock.calls[0]![1] as RequestInit;
    expect(options.body).toBeInstanceOf(FormData);
    expect(new Headers(options.headers).get('Content-Type')).toBeNull();
    response(403, { code: 'ACCESS_ERROR', message: '没有导出权限' });
    await expect(requestFile('/equipment/export')).rejects.toMatchObject({
      status: 403,
      code: 'ACCESS_ERROR',
    });
  });
});
