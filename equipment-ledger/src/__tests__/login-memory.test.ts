import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { forgetLogin, loginMemory, rememberLogin } from '../utils/loginMemory';

describe('login password memory', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  it('hands passwords to the browser and stores only account preferences locally', async () => {
    const store = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal(
      'PasswordCredential',
      class {
        constructor(public data: unknown) {}
      },
    );
    vi.stubGlobal('navigator', { credentials: { store } });
    await rememberLogin('10001', 'approver', '123456', true);
    expect(store).toHaveBeenCalledOnce();
    expect(loginMemory()).toEqual({ tenantId: '10001', account: 'approver', remember: true });
    expect(localStorage.getItem('eam-login-memory')).not.toContain('123456');
    expect(localStorage.getItem('eam-login-memory')).not.toContain('password');
    forgetLogin();
    expect(loginMemory().remember).toBe(false);
  });

  it('does not store credentials when unchecked, and clears a previous preference', async () => {
    localStorage.setItem(
      'eam-login-memory',
      JSON.stringify({ tenantId: '10001', account: 'old', remember: true }),
    );
    const store = vi.fn();
    vi.stubGlobal('navigator', { credentials: { store } });
    await rememberLogin('10001', 'next', '123456', false);
    expect(store).not.toHaveBeenCalled();
    expect(loginMemory()).toEqual({ tenantId: '10001', account: '', remember: false });
  });

  it('tolerates unavailable browser password storage and corrupted preferences', async () => {
    localStorage.setItem('eam-login-memory', '{invalid');
    expect(loginMemory().remember).toBe(false);
    vi.stubGlobal('PasswordCredential', class {});
    vi.stubGlobal('navigator', {
      credentials: { store: vi.fn().mockRejectedValue(new Error('denied')) },
    });
    await expect(rememberLogin('10001', 'approver', '123456', true)).resolves.toBeUndefined();
  });
});
