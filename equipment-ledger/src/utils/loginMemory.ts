const key = 'eam-login-memory';
type LoginMemory = { tenantId: string; account: string; remember: boolean };
type PasswordCredentialConstructor = new (data: {
  id: string;
  password: string;
  name: string;
}) => Credential;

export function loginMemory(): LoginMemory {
  try {
    const value = JSON.parse(localStorage.getItem(key) || 'null');
    if (
      value?.remember === true &&
      typeof value.tenantId === 'string' &&
      typeof value.account === 'string'
    )
      return { tenantId: value.tenantId, account: value.account, remember: true };
  } catch {
    /* Storage may be disabled. Login must remain available. */
  }
  return { tenantId: '10001', account: '', remember: false };
}

export function forgetLogin() {
  try {
    localStorage.removeItem(key);
  } catch {
    /* Optional preference storage. */
  }
}

export async function rememberLogin(
  tenantId: string,
  account: string,
  password: string,
  remember: boolean,
) {
  if (!remember) {
    forgetLogin();
    return;
  }
  try {
    localStorage.setItem(key, JSON.stringify({ tenantId, account, remember: true }));
  } catch {
    /* Optional preference storage. */
  }
  // Passwords are handed to the browser's password manager, never Web Storage.
  const PasswordCredential = (
    window as unknown as { PasswordCredential?: PasswordCredentialConstructor }
  ).PasswordCredential;
  if (!PasswordCredential || !navigator.credentials) return;
  try {
    await navigator.credentials.store(
      new PasswordCredential({ id: account, password, name: `${tenantId} / ${account}` }),
    );
  } catch {
    /* Browser policy or user dismissal must not fail a successful login. */
  }
}
