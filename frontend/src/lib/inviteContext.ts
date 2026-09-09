/** Navigation-only reference so Clerk redirects can return to the invite URL. Server revalidates the token. */
export const INVITE_TOKEN_STORAGE_KEY = 'af_pending_invite_token';

export function rememberInviteToken(token: string | null | undefined) {
  const t = token?.trim();
  if (t) sessionStorage.setItem(INVITE_TOKEN_STORAGE_KEY, t);
  else sessionStorage.removeItem(INVITE_TOKEN_STORAGE_KEY);
}

export function readRememberedInviteToken(): string {
  return sessionStorage.getItem(INVITE_TOKEN_STORAGE_KEY)?.trim() || '';
}

export function clearRememberedInviteToken() {
  sessionStorage.removeItem(INVITE_TOKEN_STORAGE_KEY);
}

export function inviteReturnPath(token: string): string {
  return `/invite/${encodeURIComponent(token)}`;
}
