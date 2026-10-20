import { describe, it, expect } from 'vitest';
import { ApiError } from '../api';
import { useApiFieldErrors } from '../composables/useApiFieldErrors';
describe('field error routing', () => {
  it('routes known fields without consuming unrelated business failures', () => {
    const form = useApiFieldErrors(['account', 'orgId']);
    expect(
      form.capture(
        new ApiError('账号重复', 400, { account: '登录账号已存在' }, 'VALIDATION_ERROR'),
      ),
    ).toBe(true);
    expect(form.errors.value.account).toBe('登录账号已存在');
    form.clear('account');
    expect(form.errors.value).toEqual({});
    expect(form.capture(new ApiError('版本已变化', 409, {}, 'DATA_CONFLICT'))).toBe(false);
    expect(form.capture(new Error('网络不可用'))).toBe(false);
  });
});
